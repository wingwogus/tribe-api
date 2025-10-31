package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.expense.dto.SettlementDto
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.repository.TripRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.abs

@Service
@Transactional(readOnly = true)
class SettlementService(
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun getDailySettlement(tripId: Long, date: LocalDate): SettlementDto.DailyResponse {
        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        val dailyExpenses = expenseRepository.findAllByTripIdAndPaymentDateBetween(tripId, date, date)
        val dailyTotalAmount = dailyExpenses.sumOf { it.totalAmount }

        val expenseSummaries = dailyExpenses.map { expense ->
            SettlementDto.DailyExpenseSummary(
                expenseId = expense.id!!,
                title = expense.title,
                payerName = expense.payer.name,
                totalAmount = expense.totalAmount
            )
        }

        // 1. 멤버별 일별 PaidAmount와 AssignedAmount를 한 번에 계산
        val memberCalcData = trip.members.map { member ->
            val paidAmount = dailyExpenses
                .filter { it.payer.id == member.id }
                .sumOf { it.totalAmount }

            val assignedAmount = dailyExpenses
                .flatMap { it.expenseItems }
                .flatMap { it.assignments }
                .filter { it.tripMember.id == member.id }
                .sumOf { it.amount }

            // Triple: (TripMember, PaidAmount, AssignedAmount)
            Triple(member, paidAmount, assignedAmount)
        }

        // 2. Member Summary DTO 생성
        val memberSummaries = memberCalcData.map { (member, paidAmount, assignedAmount) ->
            SettlementDto.MemberDailySummary(
                memberId = member.id!!,
                memberName = member.name,
                paidAmount = paidAmount,
                assignedAmount = assignedAmount
            )
        }

        // 3. Debt Relation 계산을 위한 잔액(Balance) 목록 생성 (Pair: TripMember, Balance)
        val memberBalances = memberCalcData.map { (member, paidAmount, assignedAmount) ->
            // Balance: paidAmount - assignedAmount (양수: 받을 돈, 음수: 갚을 돈)
            val balance = paidAmount.subtract(assignedAmount)
            Pair(member, balance)
        }

        // 4. 일별 최소 송금 관계 계산
        val debtRelations = calculateDebtRelations(memberBalances)

        // 5. 유효성 검사 (총 지출액과 총 분배액의 일치 여부 확인)
        val totalAssigned = memberSummaries.sumOf { it.assignedAmount }
        if (dailyTotalAmount.compareTo(totalAssigned) != 0) {
            log.error(
                "[정산 금액 불일치] Trip ID: {}, 날짜: {}. 총 지출액: {}, 총 분배액: {}",
                tripId, date, dailyTotalAmount, totalAssigned
            )
        }

        return SettlementDto.DailyResponse(
            date = date,
            dailyTotalAmount = dailyTotalAmount,
            expenses = expenseSummaries,
            memberSummaries = memberSummaries,
            debtRelations = debtRelations // 추가된 필드
        )
    }

    fun getTotalSettlement(tripId: Long): SettlementDto.TotalResponse {
        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        // 1. Repository에서 순수 데이터 배열(List<Array<Any>>)을 가져옵니다.
        val rawSummaries = expenseRepository.getSettlementSummariesAsRaw(tripId)

        // 2. 서비스 코드에서 직접 DTO 리스트로 변환합니다. (타입 문제 해결)
        val settlementSummaries = rawSummaries.map { row ->
            SettlementDto.SettlementSummary(
                tripMemberId = row[0] as Long,
                totalPaid = (row[1] as Number).let { BigDecimal(it.toString()) },
                totalAssigned = (row[2] as Number).let { BigDecimal(it.toString()) }
            )
        }
        val summaryMap = settlementSummaries.associateBy { it.tripMemberId }

        // 3. 각 멤버의 최종 잔액을 계산합니다.
        val memberBalances = trip.members.map { member ->
            val summary = summaryMap[member.id]
            val totalPaid = summary?.totalPaid ?: BigDecimal.ZERO
            val totalAssigned = summary?.totalAssigned ?: BigDecimal.ZERO
            Pair(member, totalPaid.subtract(totalAssigned))
        }

        // 4. 최소 송금 관계를 계산합니다.
        val debtRelations = calculateDebtRelations(memberBalances)

        // 5. 최종 DTO로 변환하여 반환합니다.
        val memberBalanceDtos = memberBalances.map { (member, balance) ->
            SettlementDto.MemberBalance(
                tripMemberId = member.id!!,
                nickname = member.name,
                balance = balance
            )
        }

        return SettlementDto.TotalResponse(memberBalanceDtos, debtRelations)
    }

    /**
     * 채권/채무 관계를 계산하여 최소 송금 관계로 변환합니다. (Greedy Algorithm)
     * BigDecimal 연산만 사용하여 정밀도 문제를 방지합니다.
     * @param balances Pair(TripMember, Balance) 리스트. Balance는 Paid - Assigned
     * @return 최소 송금 관계 리스트 (DebtRelation)
     */
    private fun calculateDebtRelations(balances: List<Pair<TripMember, BigDecimal>>): List<SettlementDto.DebtRelation> {
        // 잔액이 0.01 이상인 멤버만 필터링
        val cleanBalances = balances
            .filter { it.second.abs().compareTo(BigDecimal("0.01")) >= 0 } // 0.01 미만은 무시
            .sortedBy { it.second } // 오름차순 정렬: 음수(채무자) -> 양수(채권자) 순

        // 채무자(Debtor): 잔액이 음수인 멤버 (갚아야 할 돈)
        val debtors = cleanBalances.filter { it.second.signum() < 0 }.toMutableList()
        // 채권자(Creditor): 잔액이 양수인 멤버 (받아야 할 돈)
        val creditors = cleanBalances.filter { it.second.signum() > 0 }.toMutableList()

        val relations = mutableListOf<SettlementDto.DebtRelation>()

        // 0.01 미만 비교용 상수
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

            // 잔액 업데이트
            // 채무자는 갚았으므로 잔액이 0에 가까워짐 (음수 -> 0)
            debtorBalance += transferAmount
            // 채권자는 받았으므로 잔액이 0에 가까워짐 (양수 -> 0)
            creditorBalance -= transferAmount

            // 0.01 미만이면 정산 완료로 간주하여 리스트에서 제거 (BigDecimal 비교)
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