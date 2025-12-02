package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.exchange.entity.Currency
import com.tribe.tribe_api.exchange.repository.CurrencyRepository
import com.tribe.tribe_api.expense.dto.SettlementDto
import com.tribe.tribe_api.expense.dto.SettlementDto.MemberSettlementData
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class SettlementService(
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository,
    private val currencyRepository: CurrencyRepository,
    private val entityManager: EntityManager
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    private val KRW = "KRW" // 기준 통화 정의
    private val SCALE = 0 // 정산은 원화 단위(0)로 처리
    private val FOREIGN_CURRENCY_SCALE = 2
    private val EPSILON = BigDecimal("1.00")

    /**
     * 특정 날짜를 기준으로 과거와 미래를 통틀어 가장 가까운 환율을 찾습니다.
     * 비효율적인 일일 단위 검색을 JPQL 쿼리로 대체하여 성능을 개선합니다.
     */
    private fun findClosestRate(currencyCode: String, targetDate: LocalDate): Currency? {
        // 1. 정확히 일치하는 날짜가 있는지 확인
        val exactMatch = currencyRepository.findByCurUnitAndDate(currencyCode, targetDate)
        if (exactMatch != null) return exactMatch

        // 2. 가장 가까운 과거 환율 조회
        val pastRate = currencyRepository.findTopByCurUnitAndDateLessThanEqualOrderByDateDesc(
            currencyCode,
            targetDate
        )

        // 3. 가장 가까운 미래 환율 조회
        val futureRate = currencyRepository.findTopByCurUnitAndDateGreaterThanEqualOrderByDateAsc(
            currencyCode,
            targetDate
        )

        // 4. 거리 비교 및 선택
        return when {
            pastRate != null && futureRate == null -> pastRate
            pastRate == null && futureRate != null -> futureRate
            pastRate != null && futureRate != null -> {
                // 과거/미래 날짜 간의 거리만 비교합니다.
                val pastDistance = ChronoUnit.DAYS.between(pastRate.date, targetDate).coerceAtLeast(0)
                val futureDistance = ChronoUnit.DAYS.between(targetDate, futureRate.date).coerceAtLeast(0)

                // 거리가 짧거나 같으면 과거 환율을 선택 (과거 데이터 선호)
                if (pastDistance <= futureDistance) {
                    pastRate
                } else {
                    futureRate
                }
            }
            else -> null
        }
    }

    /**
     * 외화 금액을 지출일 환율을 적용하여 KRW로 변환합니다.
     */
    private fun convertToKrw(amount: BigDecimal, expense: Expense): BigDecimal {
        val currencyCode = expense.currency?.uppercase()

        val tripStartDate = expense.trip.startDate
        val categoryDay = expense.itineraryItem.category.day
        val currentDate = tripStartDate.plusDays(categoryDay.toLong() - 1) // paymentDate 대체

        if (currencyCode == KRW || currencyCode.isNullOrBlank()) {
            return amount.setScale(SCALE, RoundingMode.HALF_UP)
        }

        // 가장 가까운 환율을 찾는 헬퍼 함수 호출
        val currencyRate = findClosestRate(currencyCode, currentDate)

        // 환율을 찾지 못했으면 예외 발생
        if (currencyRate == null) {
            log.error("Exchange rate not found for {} on or near {}", currencyCode, currentDate)
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

        // 1. 날짜(date)를 기반으로 여행의 '일차(day)'를 계산합니다.
        // 일차 = (조회 날짜 - 여행 시작일) + 1
        val dayToFilter = ChronoUnit.DAYS.between(trip.startDate, date).toInt() + 1

        // 2. 여행의 모든 지출을 가져오는 대신, 해당 일차(day)에 해당하는 지출만 DB에서 필터링하여 가져옵니다.
        val dailyExpenses: List<Expense> = expenseRepository.findDailyExpensesWithDetailsByTripIdAndDay(tripId, dayToFilter)

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

        // 3. 일별 최소 송금 관계 계산 (통화별 계산 로직 사용)
        val rateLookup: (String) -> BigDecimal? = { currencyCode ->
            findClosestRate(currencyCode, date)?.exchangeRate
        }
        val debtRelations = calculateDebtRelationsByCurrency(trip.members, dailyExpenses, rateLookup)


        // 4. 유효성 검사 (Total Assigned는 여전히 B에게 할당된 전체 KRW 금액을 사용해야 함)
        val totalAssignedKrw = memberSummaries.sumOf { it.assignedAmount }

        val difference = dailyTotalAmountKrw.subtract(totalAssignedKrw).abs()
        if (difference > EPSILON) {
            log.warn(
                "[정산 금액 불일치] Trip ID: {}, 날짜: {}. 총 지출액(KRW): {}, 총 분배액(KRW): {}",
                tripId, date, dailyTotalAmountKrw, totalAssignedKrw
            )
        }

        return SettlementDto.DailyResponse(
            date = date,
            dailyTotalAmount = dailyTotalAmountKrw, // KRW 변환된 총액
            expenses = expenseSummaries,
            memberSummaries = memberSummaries,
            debtRelations = debtRelations // 통화별로 분리된 목록 반환
        )
    }

    /**
     * 전체 정산 로직: 모든 지출 내역에 대해 환율을 적용하여 KRW 기준으로 잔액을 계산합니다.
     */
    fun getTotalSettlement(tripId: Long): SettlementDto.TotalResponse {
        entityManager.clear()
        val trip = tripRepository.findById(tripId)
            .orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }

        val allExpenses: List<Expense> = expenseRepository.findAllWithDetailsByTripId(tripId)

        // 1. 멤버별 PaidAmount(KRW)와 AssignedAmount(KRW) 계산 (추출된 메서드 사용)
        val memberCalcData = calculateMemberSettlementData(trip, allExpenses)

        // 2. 잔액(Balance) 목록 생성 (KRW 기준) 및 DTO 변환
        val memberBalanceDtos = memberCalcData.map { data ->
            val balance = data.paidAmountKrw.subtract(data.assignedAmountKrw)
            SettlementDto.MemberBalance(
                tripMemberId = data.member.id!!,
                nickname = data.member.name,
                balance = balance,
                foreignCurrenciesUsed = data.foreignCurrencies
            )
        }

        // 3. 최소 송금 관계 계산 (통화별 계산 로직 사용)
        val rateLookup: (String) -> BigDecimal? = { currencyCode ->
            currencyRepository.findTopByCurUnitOrderByDateDesc(currencyCode)?.exchangeRate
        }
        val debtRelations = calculateDebtRelationsByCurrency(trip.members, allExpenses, rateLookup)


        // 4. DTO 변환 및 반환
        val totalPaidSum = memberBalanceDtos.sumOf { it.balance.max(BigDecimal.ZERO) }
        val totalAssignedSum = memberBalanceDtos.sumOf { it.balance.negate().max(BigDecimal.ZERO) }

        val difference = totalPaidSum.subtract(totalAssignedSum).abs()
        if (difference > EPSILON) {
            log.warn(
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
     * 1:1 관계와 다자간 관계에서 통화별로 부채 관계를 계산하여 최소 송금 관계 목록을 생성
     *
     * @param members 정산에 참여하는 모든 TripMembers
     * @param expenses 정산에 사용될 지출 목록 (일일 또는 전체)
     * @param rateLookup 통화 코드에 해당하는 환율을 조회하는 함수
     * @return 모든 통화를 고려한 최소 송금 관계 목록
     */
    private fun calculateDebtRelationsByCurrency(
        members: List<TripMember>,
        expenses: List<Expense>,
        rateLookup: (currencyCode: String) -> BigDecimal?
    ): List<SettlementDto.DebtRelation> {
        val allDebtRelations = mutableListOf<SettlementDto.DebtRelation>()

        // 1. 모든 고유 통화 코드 (KRW 포함) 추출
        val allCurrencies = expenses
            .mapNotNull { it.currency?.uppercase() }
            .distinct()
            .plus(KRW)
            .distinct()

        // 2. 각 통화별로 최소 송금 관계 계산
        for (currencyCode in allCurrencies) {
            val isForeign = currencyCode != KRW
            val rate = if (isForeign) rateLookup(currencyCode) else BigDecimal.ONE

            if (rate == null) {
                if (isForeign) log.warn("환율을 찾을 수 없어 {} 통화를 정산에서 제외합니다.", currencyCode)
                continue
            }

            // 2-1. 해당 통화에 대한 멤버별 순 잔액 계산 (원화 기준 아님)
            val balancesInCurrency = members.map { member ->
                val paid = expenses
                    .filter { it.payer.id == member.id && it.currency?.uppercase() == currencyCode }
                    .sumOf { it.totalAmount }

                val assigned = expenses
                    .flatMap { it.expenseItems }
                    .flatMap { it.assignments }
                    .distinct() // 중복 제거
                    .filter { it.tripMember.id == member.id && it.expenseItem.expense.currency?.uppercase() == currencyCode }
                    .sumOf { it.amount }

                member to paid.subtract(assigned) // Pair<TripMember, BigDecimal>
            }

            // 2-2. 해당 통화에 대한 잔액이 유의미한지 확인 (모든 잔액의 절댓값 합이 0.01보다 작은 경우 무시)
            val totalAbsBalance = balancesInCurrency.sumOf { it.second.abs() }
            if (totalAbsBalance < BigDecimal("0.01")) {
                continue
            }

            // 2-3. 해당 통화에 대한 최소 송금 관계 계산
            val relationsInCurrency = calculateMinimalTransfers(balancesInCurrency, currencyCode, rate)
            allDebtRelations.addAll(relationsInCurrency)
        }

        return allDebtRelations
    }

    /**
     * 특정 통화에 대한 잔액 목록을 기반으로 최소 송금 관계를 계산 (Greedy)
     *
     * @param balances 멤버와 해당 통화 잔액을 포함하는 목록
     * @param currencyCode 현재 계산 중인 통화 코드
     * @param exchangeRate KRW로 변환하기 위한 환율
     * @return 해당 통화에 대한 최소 송금 관계 목록
     */
    private fun calculateMinimalTransfers(
        balances: List<Pair<TripMember, BigDecimal>>,
        currencyCode: String,
        exchangeRate: BigDecimal
    ): List<SettlementDto.DebtRelation> {
        val epsilon = BigDecimal("0.01")

        val cleanBalances = balances
            .filter { it.second.abs() >= epsilon }
            .sortedBy { it.second }

        val debtors = cleanBalances.filter { it.second.signum() < 0 }.toMutableList()
        val creditors = cleanBalances.filter { it.second.signum() > 0 }.toMutableList()
        val relations = mutableListOf<SettlementDto.DebtRelation>()

        val isForeign = currencyCode != KRW

        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val debtorPair = debtors.first()
            val creditorPair = creditors.first()

            val debtor = debtorPair.first
            var debtorBalance = debtorPair.second
            val creditor = creditorPair.first
            var creditorBalance = creditorPair.second

            val transferAmountOriginal = debtorBalance.abs().min(creditorBalance)

            debtorBalance += transferAmountOriginal
            creditorBalance -= transferAmountOriginal

            val krwAmount = transferAmountOriginal.multiply(exchangeRate).setScale(SCALE, RoundingMode.HALF_UP)

            // KRW 1원 미만의 거래는 생성하지 않음
            if (krwAmount >= EPSILON) {
                relations.add(
                    SettlementDto.DebtRelation(
                        fromNickname = debtor.name,
                        fromTripMemberId = debtor.id!!,
                        toNickname = creditor.name,
                        toTripMemberId = creditor.id!!,
                        amount = krwAmount, // KRW 송금액
                        equivalentOriginalAmount = if (isForeign) transferAmountOriginal.setScale(FOREIGN_CURRENCY_SCALE, RoundingMode.HALF_UP) else null,
                        originalCurrencyCode = if (isForeign) currencyCode else null
                    )
                )
            }

            if (debtorBalance.abs() < epsilon) {
                debtors.removeAt(0)
            } else {
                debtors[0] = debtor to debtorBalance
            }

            if (creditorBalance.abs() < epsilon) {
                creditors.removeAt(0)
            } else {
                creditors[0] = creditor to creditorBalance
            }
        }
        return relations
    }
}
