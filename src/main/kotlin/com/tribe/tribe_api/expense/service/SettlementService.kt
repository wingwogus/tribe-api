package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.expense.dto.SettlementDto
import com.tribe.tribe_api.expense.dto.SettlementDto.MemberSettlementData
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.exchange.entity.Currency
import com.tribe.tribe_api.exchange.repository.CurrencyRepository
import com.tribe.tribe_api.exchange.service.ExchangeRateService
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class SettlementService(
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository,
    private val currencyRepository: CurrencyRepository,
    private val exchangeRateService: ExchangeRateService,
    private val entityManager: EntityManager
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val KRW = "KRW" // 기준 통화 정의
    private val SCALE = 0 // 정산은 원화 단위(0)로 처리
    private val EPSILON = BigDecimal("1.00")

    /**
     * 외화 금액을 지출일 환율을 적용하여 KRW로 변환합니다.
     * DB에 없으면, 최대 7일 전까지 역순으로 유효한 환율을 찾습니다.
     */
    private fun convertToKrw(amount: BigDecimal, expense: Expense): BigDecimal {
        val currencyCode = expense.currency?.uppercase()

        if (currencyCode == KRW || currencyCode.isNullOrBlank()) {
            return amount.setScale(SCALE, RoundingMode.HALF_UP)
        }

        var currentDate = expense.paymentDate
        var currencyRate: Currency? = null
        val MAX_DAYS_BACK = 7

        // 1. DB에서 환율을 찾아 거슬러 올라갑니다. (최대 7일)
        for (i in 0 until MAX_DAYS_BACK) {
            // 2. DB 조회 (가장 먼저 수행)
            currencyRate = currencyRepository.findByCurUnitAndDate(currencyCode, currentDate)
            if (currencyRate != null) {
                break // DB에 있으면 바로 사용 (2025-10-24 데이터가 26일 요청 시 여기에 걸려야 함)
            }

            // 3. 현재 날짜가 주말인지 확인합니다.
            val dayOfWeek = currentDate.dayOfWeek
            val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

            // 주말인 경우 API 호출 시도 없이 바로 다음 날짜로 이동
            if (isWeekend) {
                currentDate = currentDate.minusDays(1)
                continue // 다음 루프 실행
            }

            // 4. 다음 날짜로 이동 (API 호출 로직이 없으므로 남은 평일 스텝)
            currentDate = currentDate.minusDays(1)
        }

        // 환율을 찾지 못했으면 예외 발생
        if (currencyRate == null) {
            log.error("Exchange rate not found for {} on or before {}", currencyCode, expense.paymentDate)
            throw BusinessException(ErrorCode.EXCHANGE_RATE_NOT_FOUND)
        }

        val exchangeRate = currencyRate.exchangeRate

        // 금액 * 환율 = KRW 금액
        return amount.multiply(exchangeRate)
            .setScale(SCALE, RoundingMode.HALF_UP)
    }


    fun getDailySettlement(tripId: Long, date: LocalDate): SettlementDto.DailyResponse {
        // [핵심 추가]: JPA 영속성 컨텍스트(1차 캐시)를 무효화하여 DB에서 강제로 데이터를 읽어오도록 합니다.
        entityManager.clear()

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

        // 1. 멤버별 PaidAmount(KRW)와 AssignedAmount(KRW) 계산 (추출된 메서드 사용)
        val memberCalcData = calculateMemberSettlementData(trip, dailyExpenses)

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

        // 4. 일별 최소 송금 관계 계산 (동적 환율 적용)
        val debtCurrencyCode = dailyExpenses.firstOrNull { it.currency != KRW && it.currency != null }?.currency?.uppercase() ?: KRW

        val debtExchangeRate = if (debtCurrencyCode != KRW) {
            currencyRepository.findByCurUnitAndDate(debtCurrencyCode, date)?.exchangeRate
                ?: run {
                    var currentDate = date
                    var rate: Currency? = null
                    for (i in 0 until 7) {
                        rate = currencyRepository.findByCurUnitAndDate(debtCurrencyCode, currentDate)
                        if (rate != null) break
                        currentDate = currentDate.minusDays(1)
                    }
                    rate?.exchangeRate ?: throw BusinessException(ErrorCode.EXCHANGE_RATE_NOT_FOUND)
                }
        } else {
            BigDecimal.ONE // KRW는 환율 1
        }

        val debtRelations = calculateDebtRelations(
            memberBalances,
            debtCurrencyCode,
            debtExchangeRate
        )


        // 5. 유효성 검사
        val totalAssignedKrw = memberSummaries.sumOf { it.assignedAmount }

        val difference = dailyTotalAmountKrw.subtract(totalAssignedKrw).abs()
        if (difference.compareTo(EPSILON) > 0) {
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
        entityManager.clear()
        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        val allExpenses: List<Expense> = expenseRepository.findAllByTripId(tripId)

        // 1. 멤버별 PaidAmount(KRW)와 AssignedAmount(KRW) 계산 (추출된 메서드 사용)
        val memberCalcData = calculateMemberSettlementData(trip, allExpenses)

        // 3. 잔액(Balance) 목록 생성 (KRW 기준)
        val memberBalances = memberCalcData.map { data ->
            val balance = data.paidAmountKrw.subtract(data.assignedAmountKrw)
            SettlementDto.MemberBalance(
                tripMemberId = data.member.id!!,
                nickname = data.member.name,
                balance = balance,
                foreignCurrenciesUsed = data.foreignCurrencies
            ) to Pair(data.member, balance)
        }

        // 4. 최소 송금 관계 계산 (동적 환율 적용)
        val assumedCountryCode = trip.country.code.uppercase()

        val debtCurrencyCode = when (assumedCountryCode) {
            "JP" -> "JPY"
            "US" -> "USD"
            "KR" -> KRW
            else -> assumedCountryCode
        }

        val debtExchangeRate = if (debtCurrencyCode != KRW) {
            // 최신 환율이 없을 경우 BigDecimal.ONE 대신 예외를 발생시켜 정산 오류를 방지
            currencyRepository.findTopByCurUnitOrderByDateDesc(debtCurrencyCode)?.exchangeRate
                ?: throw BusinessException(ErrorCode.EXCHANGE_RATE_NOT_FOUND)
        } else {
            BigDecimal.ONE // KRW는 환율 1
        }

        val debtRelations = calculateDebtRelations(
            memberBalances.map { it.second },
            debtCurrencyCode,
            debtExchangeRate
        )


        // 5. DTO 변환 및 반환
        val memberBalanceDtos = memberBalances.map { it.first }

        val totalPaidSum = memberBalanceDtos.sumOf { it.balance.max(BigDecimal.ZERO) }
        val totalAssignedSum = memberBalanceDtos.sumOf { it.balance.negate().max(BigDecimal.ZERO) }

        val difference = totalPaidSum.subtract(totalAssignedSum).abs()
        if (difference.compareTo(EPSILON) > 0) {
            log.error(
                "[전체 정산 금액 불일치] Trip ID: {}. 총 Paid(KRW): {}, 총 Assigned(KRW): {}",
                tripId, totalPaidSum, totalAssignedSum
            )
        }

        return SettlementDto.TotalResponse(memberBalanceDtos, debtRelations)
    }

    /**
     * 특정 지출 목록을 기반으로 멤버별 정산 데이터를 계산합니다. (PaidAmount/AssignedAmount/ForeignCurrencies)
     */
    private fun calculateMemberSettlementData(trip: Trip, expenses: List<Expense>): List<MemberSettlementData> {
        return trip.members.map { member ->
            // Paid Amount (KRW) 합산
            val paidAmountKrw = expenses
                .filter { it.payer.id == member.id }
                .sumOf { expense -> convertToKrw(expense.totalAmount, expense) }

            // Assigned Amount (KRW) 합산
            val assignedAmountKrw = expenses
                .flatMap { it.expenseItems }
                .flatMap { it.assignments }
                .distinct() // 👈 FIX: Fetch Join으로 인한 중복 엔티티 제거
                .filter { it.tripMember.id == member.id }
                .sumOf { assignment ->
                    val expense = assignment.expenseItem.expense
                    convertToKrw(assignment.amount, expense)
                }

            // New: 해당 멤버가 지출했거나 분담받은 모든 외화 통화 코드 수집
            val foreignCurrencies = expenses
                .filter { expense ->
                    (expense.payer.id == member.id) ||
                            expense.expenseItems.any { item ->
                                item.assignments.any { assign -> assign.tripMember.id == member.id }
                            }
                }
                .mapNotNull { it.currency }
                .filter { it != KRW }
                .distinct()
                .toList()

            MemberSettlementData(member, paidAmountKrw, assignedAmountKrw, foreignCurrencies)
        }
    }


    /**
     * 채권/채무 관계를 계산하여 최소 송금 관계로 변환합니다. (Greedy Algorithm)
     */
    private fun calculateDebtRelations(
        balances: List<Pair<TripMember, BigDecimal>>,
        assumedCurrencyCode: String,
        assumedExchangeRate: BigDecimal
    ): List<SettlementDto.DebtRelation> {
        // 잔액이 0.01 이상인 멤버만 필터링
        val cleanBalances = balances
            .filter { it.second.abs().compareTo(BigDecimal("0.01")) >= 0 }
            .sortedBy { it.second }

        val debtors = cleanBalances.filter { it.second.signum() < 0 }.toMutableList()
        val creditors = cleanBalances.filter { it.second.signum() > 0 }.toMutableList()
        val relations = mutableListOf<SettlementDto.DebtRelation>()

        val epsilon = BigDecimal("0.01")

        // KRW가 아닌 통화인지 확인 (KRW는 환율이 1.0)
        val isForeignCurrency = assumedExchangeRate.compareTo(BigDecimal.ONE) != 0


        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val debtorPair = debtors.first()
            val creditorPair = creditors.first()

            val debtor = debtorPair.first
            var debtorBalance = debtorPair.second
            val creditor = creditorPair.first
            var creditorBalance = creditorPair.second

            // 송금액: 채무액(음수 잔액의 절댓값)과 채권액) 중 작은 값. BigDecimal.min() 사용
            val transferAmount = debtorBalance.abs().min(creditorBalance)

            // 💡 수정된 로직: 원본 통화 금액 계산 (KRW 금액 / 동적으로 결정된 환율)
            // 소수점 0자리로 반올림
            val equivalentOriginalAmount = if (isForeignCurrency) {
                // 외화인 경우: KRW 송금액을 환율로 나누어 원본 통화 금액을 역추산
                transferAmount.divide(assumedExchangeRate, 0, RoundingMode.HALF_UP)
            } else {
                null
            }

            val originalCurrencyCode = if (isForeignCurrency) assumedCurrencyCode else null


            relations.add(
                SettlementDto.DebtRelation(
                    fromNickname = debtor.name,
                    fromTripMemberId = debtor.id!!,
                    toNickname = creditor.name,
                    toTripMemberId = creditor.id!!,
                    amount = transferAmount, // KRW 송금 금액
                    equivalentOriginalAmount = equivalentOriginalAmount, // 원본 통화 금액
                    originalCurrencyCode = originalCurrencyCode           // 원본 통화 코드
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