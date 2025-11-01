package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.expense.dto.SettlementDto
import com.tribe.tribe_api.expense.dto.SettlementDto.MemberSettlementData
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.exchange.repository.CurrencyRepository
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.repository.TripRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class SettlementService(
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository,
    private val currencyRepository: CurrencyRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val KRW = "KRW" // 기준 통화 정의
    private val SCALE = 0 // 정산은 원화 단위(0)로 처리

    /**
     * 외화 금액을 지출일 환율을 적용하여 KRW로 변환합니다.
     */
    private fun convertToKrw(amount: BigDecimal, expense: Expense): BigDecimal {
        // expense.currency를 안전하게 가져와서 사용
        val currencyCode = expense.currency?.uppercase()

        if (currencyCode == KRW || currencyCode.isNullOrBlank()) {
            return amount.setScale(SCALE, RoundingMode.HALF_UP)
        }

        // 지출일과 통화 코드로 환율 조회
        val currencyRate = currencyRepository.findByCurUnitAndDate(currencyCode, expense.paymentDate)
            ?: throw BusinessException(ErrorCode.EXCHANGE_RATE_NOT_FOUND)

        // 금액 * 환율 = KRW 금액
        return amount.multiply(currencyRate.exchangeRate)
            .setScale(SCALE, RoundingMode.HALF_UP)
    }

    fun getDailySettlement(tripId: Long, date: LocalDate): SettlementDto.DailyResponse {
        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        val dailyExpenses: List<Expense> = expenseRepository.findAllByTripIdAndPaymentDateBetween(tripId, date, date)

        // 총 지출액을 KRW로 변환하여 합산
        val dailyTotalAmountKrw = dailyExpenses.sumOf { expense ->
            convertToKrw(expense.totalAmount, expense)
        }

        val expenseSummaries = dailyExpenses.map { expense ->
            SettlementDto.DailyExpenseSummary(
                expenseId = expense.id!!,
                title = expense.title,
                payerName = expense.payer.name,
                totalAmount = convertToKrw(expense.totalAmount, expense), // KRW 금액
                originalAmount = expense.totalAmount,                      // 원본 금액
                currencyCode = expense.currency ?: KRW                     // 통화 코드
            )
        }

        // 1. 멤버별 일별 PaidAmount(KRW)와 AssignedAmount(KRW)를 한 번에 계산
        val memberCalcData = trip.members.map { member ->
            // Paid Amount (KRW) 합산
            val paidAmountKrw = dailyExpenses
                .filter { it.payer.id == member.id }
                .sumOf { expense -> convertToKrw(expense.totalAmount, expense) }

            // Assigned Amount (KRW) 합산
            val assignedAmountKrw = dailyExpenses
                .flatMap { it.expenseItems }
                .flatMap { it.assignments }
                .filter { it.tripMember.id == member.id }
                .sumOf { assignment ->
                    val expense = assignment.expenseItem.expense
                    convertToKrw(assignment.amount, expense)
                }

            // New: 해당 멤버가 지출했거나 분담받은 모든 외화 통화 코드 수집
            val foreignCurrencies = dailyExpenses
                .filter { expense ->
                    val isPayer = expense.payer.id == member.id
                    val isAssignee = expense.expenseItems
                        .flatMap { it.assignments }
                        .any { it.tripMember.id == member.id }
                    isPayer || isAssignee
                }
                .mapNotNull { it.currency }
                .filter { it != KRW }
                .distinct()
                .toList()

            MemberSettlementData(member, paidAmountKrw, assignedAmountKrw, foreignCurrencies)
        }

        // 2. Member Summary DTO 생성
        val memberSummaries = memberCalcData.map { data ->
            SettlementDto.MemberDailySummary(
                memberId = data.member.id!!,
                memberName = data.member.name,
                paidAmount = data.paidAmountKrw,
                assignedAmount = data.assignedAmountKrw
            )
        }

        // 3. Debt Relation 계산을 위한 잔액(Balance) 목록 생성
        val memberBalances = memberCalcData.map { data ->
            // Balance: paidAmount - assignedAmount (KRW 기준)
            val balance = data.paidAmountKrw.subtract(data.assignedAmountKrw)
            Pair(data.member, balance)
        }

        // 4. 일별 최소 송금 관계 계산
        val debtRelations = calculateDebtRelations(memberBalances)

        // 5. 유효성 검사
        val totalAssignedKrw = memberSummaries.sumOf { it.assignedAmount }
        if (dailyTotalAmountKrw.compareTo(totalAssignedKrw) != 0) {
            log.error(
                "[정산 금액 불일치] Trip ID: {}, 날짜: {}. 총 지출액(KRW): {}, 총 분배액(KRW): {}",
                tripId, date, dailyTotalAmountKrw, totalAssignedKrw
            )
        }

        return SettlementDto.DailyResponse(
            date = date,
            dailyTotalAmount = dailyTotalAmountKrw, // KRW 변환된 총액
            expenses = expenseSummaries,
            memberSummaries = memberSummaries,
            debtRelations = debtRelations
        )
    }

    /**
     * 전체 정산 로직: 모든 지출 내역에 대해 환율을 적용하여 KRW 기준으로 잔액을 계산합니다.
     */
    fun getTotalSettlement(tripId: Long): SettlementDto.TotalResponse {
        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        // 1. 여행의 모든 지출 내역을 가져옵니다.
        val allExpenses: List<Expense> = expenseRepository.findAllByTripId(tripId)

        // 2. 멤버별 PaidAmount(KRW), AssignedAmount(KRW), 그리고 사용된 외화를 계산합니다.
        val memberCalcData = trip.members.map { member ->

            // Paid Amount (KRW) 합산
            val paidAmountKrw = allExpenses
                .filter { it.payer.id == member.id }
                .sumOf { expense -> convertToKrw(expense.totalAmount, expense) }

            // Assigned Amount (KRW) 합산
            val assignedAmountKrw = allExpenses
                .flatMap { it.expenseItems }
                .flatMap { it.assignments }
                .filter { it.tripMember.id == member.id }
                .sumOf { assignment ->
                    val expense = assignment.expenseItem.expense
                    convertToKrw(assignment.amount, expense)
                }

            // New: 해당 멤버가 지출했거나 분담받은 모든 외화 통화 코드 수집
            val foreignCurrencies = allExpenses
                .filter { expense ->
                    val isPayer = expense.payer.id == member.id
                    val isAssignee = expense.expenseItems
                        .flatMap { it.assignments }
                        .any { it.tripMember.id == member.id }
                    isPayer || isAssignee
                }
                .mapNotNull { it.currency }
                .filter { it != KRW }
                .distinct()
                .toList()

            MemberSettlementData(member, paidAmountKrw, assignedAmountKrw, foreignCurrencies)
        }

        // 3. 잔액(Balance) 목록 생성 (KRW 기준)
        val memberBalances = memberCalcData.map { data ->
            val balance = data.paidAmountKrw.subtract(data.assignedAmountKrw)
            // MemberBalance DTO에 외화 정보와 잔액을 함께 묶음
            SettlementDto.MemberBalance(
                tripMemberId = data.member.id!!,
                nickname = data.member.name,
                balance = balance,
                foreignCurrenciesUsed = data.foreignCurrencies
            ) to Pair(data.member, balance)
        }

        // 4. 최소 송금 관계 계산
        val debtRelations = calculateDebtRelations(memberBalances.map { it.second })

        // 5. DTO 변환 및 반환
        val memberBalanceDtos = memberBalances.map { it.first } // MemberBalance DTO만 추출

        // 유효성 검사 (총 Paid와 총 Assigned의 합이 0에 가까운지 확인)
        val totalPaidSum = memberBalanceDtos.sumOf { it.balance.max(BigDecimal.ZERO) }
        val totalAssignedSum = memberBalanceDtos.sumOf { it.balance.negate().max(BigDecimal.ZERO) }

        if (totalPaidSum.compareTo(totalAssignedSum) != 0) {
            log.error(
                "[전체 정산 금액 불일치] Trip ID: {}. 총 Paid(KRW): {}, 총 Assigned(KRW): {}",
                tripId, totalPaidSum, totalAssignedSum
            )
        }

        return SettlementDto.TotalResponse(memberBalanceDtos, debtRelations)
    }

    /**
     * 채권/채무 관계를 계산하여 최소 송금 관계로 변환합니다. (Greedy Algorithm)
     */
    private fun calculateDebtRelations(balances: List<Pair<TripMember, BigDecimal>>): List<SettlementDto.DebtRelation> {
        // 잔액이 0.01 이상인 멤버만 필터링
        val cleanBalances = balances
            .filter { it.second.abs().compareTo(BigDecimal("0.01")) >= 0 }
            .sortedBy { it.second }

        val debtors = cleanBalances.filter { it.second.signum() < 0 }.toMutableList()
        val creditors = cleanBalances.filter { it.second.signum() > 0 }.toMutableList()
        val relations = mutableListOf<SettlementDto.DebtRelation>()

        val epsilon = BigDecimal("0.01")

        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val debtorPair = debtors.first()
            val creditorPair = creditors.first()

            val debtor = debtorPair.first
            var debtorBalance = debtorPair.second
            val creditor = creditorPair.first
            var creditorBalance = creditorPair.second

            // 송금액: 채무액(음수 잔액의 절댓값)과 채권액 중 작은 값. BigDecimal.min() 사용
            val transferAmount = debtorBalance.abs().min(creditorBalance)

            relations.add(
                SettlementDto.DebtRelation(
                    fromNickname = debtor.name,
                    fromTripMemberId = debtor.id!!,
                    toNickname = creditor.name,
                    toTripMemberId = creditor.id!!,
                    amount = transferAmount
                )
            )

            debtorBalance += transferAmount
            creditorBalance -= transferAmount

            if (debtorBalance.abs().compareTo(epsilon) < 0) {
                debtors.removeAt(0)
            } else {
                debtors[0] = debtor to debtorBalance
            }

            if (creditorBalance.abs().compareTo(epsilon) < 0) {
                creditors.removeAt(0)
            } else {
                creditors[0] = creditor to creditorBalance
            }
        }
        return relations
    }
}