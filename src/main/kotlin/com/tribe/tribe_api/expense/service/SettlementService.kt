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
import java.time.temporal.ChronoUnit // 날짜 차이 계산을 위해 추가

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
    private val FOREIGN_CURRENCY_SCALE = 2
    private val EPSILON = BigDecimal("1.00")
    private val MIN_DATE = LocalDate.of(2000, 1, 1)
    private val MAX_DATE = LocalDate.of(2100, 1, 1) // 충분히 먼 미래 날짜

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
        var currentDate = tripStartDate.plusDays(categoryDay.toLong() - 1) // paymentDate 대체

        if (currencyCode == KRW || currencyCode.isNullOrBlank()) {
            return amount.setScale(SCALE, RoundingMode.HALF_UP)
        }

        // [수정] 가장 가까운 환율을 찾는 헬퍼 함수 호출로 대체
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

        val allExpensesWithDetails: List<Expense> = expenseRepository.findAllWithDetailsByTripId(tripId)

        val dailyExpenses: List<Expense> = allExpensesWithDetails.filter { expense ->
            val tripStartDate = expense.trip.startDate
            val categoryDay = expense.itineraryItem.category.day
            // 실시간 날짜 계산: (여행 시작일) + (일차 - 1)
            val expenseDate = tripStartDate.plusDays(categoryDay.toLong() - 1)
            expenseDate == date
        }

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

        // 4. 일별 최소 송금 관계 계산 (통화별 분리 로직 또는 최소 송금 알고리즘)
        val debtRelations = mutableListOf<SettlementDto.DebtRelation>()

        // 1. 잔액 목록에서 0.01 KRW 미만의 잔액을 제거하여 실제 채권/채무 관계만 남깁니다.
        val cleanBalances = memberBalances
            .filter { it.second.abs().compareTo(EPSILON) >= 0 }
            .map { it.first to it.second } // Pair<TripMember, BigDecimal>

        if (cleanBalances.size == 2) {
            // Case 1: Simple 1:1 Debt (정확히 2명만 잔액이 남은 경우) -> 통화별 분리 로직 적용

            // Rate Lookup for Daily Settlement (date-based lookup)
            val dailyRateLookup: (String) -> BigDecimal? = { currencyCode ->
                findClosestRate(currencyCode, date)?.exchangeRate
            }

            debtRelations.addAll(
                calculateOneToOneDebtRelations(cleanBalances, dailyExpenses, dailyRateLookup)
            )

        } else if (cleanBalances.size > 2) {
            // Case 2: Multi-party Debt (Minimal Transfer Algorithm 사용)
            // Daily Settlement는 KRW 기준으로 Minimal Transfer를 실행하여 최소 송금 관계를 제공합니다.
            log.warn("Multi-party debt detected for daily settlement on {}. Using Minimal Transfer algorithm (KRW only).", date)

            debtRelations.addAll(
                calculateDebtRelations(
                    cleanBalances,
                    KRW, // KRW 기준으로 정산 통일
                    BigDecimal.ONE // KRW 환율
                )
            )
        }


        // 5. 유효성 검사 (Total Assigned는 여전히 B에게 할당된 전체 KRW 금액을 사용해야 함)
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

        // 4. 최소 송금 관계 계산 (통화별 분리 또는 최소 송금 로직 적용)
        val debtRelations = mutableListOf<SettlementDto.DebtRelation>()

        // 1. 잔액 목록에서 Debtor와 Creditor 식별 및 잔액 목록 정리
        val balancesForDebtCalc = memberBalances.map { it.second } // Pair<TripMember, BigDecimal>
        val cleanBalances = balancesForDebtCalc.filter { it.second.abs().compareTo(EPSILON) >= 0 }

        // 2. 1:1 관계인지 확인 (정확히 두 명만 잔액이 남아 있어야 함)
        if (cleanBalances.size == 2) {
            // Case 1: Simple 1:1 Debt (통화별 분리 로직 적용)

            // Rate Lookup for Total Settlement (latest rate lookup)
            val totalRateLookup: (String) -> BigDecimal? = { currencyCode ->
                currencyRepository.findTopByCurUnitOrderByDateDesc(currencyCode)?.exchangeRate
            }

            debtRelations.addAll(
                calculateOneToOneDebtRelations(cleanBalances, allExpenses, totalRateLookup)
            )

        } else if (cleanBalances.size > 2) {
            // Case 2: Multi-party Debt (최소 송금 알고리즘 사용)
            log.warn("Multi-party debt detected for trip ID {}. Using Minimal Transfer algorithm.", tripId)

            // 2.1. 단일 정산 통화 결정 (최고 지출 외화 우선 로직 재사용)
            val allForeignCurrenciesUsed = allExpenses
                .mapNotNull { it.currency }
                .filter { it != KRW }
                .distinct()
                .toList()

            val singleDebtCurrencyCode = when (allForeignCurrenciesUsed.size) {
                0 -> trip.country.code.uppercase() // KRW만 사용 시 여행 국가 통화
                1 -> allForeignCurrenciesUsed.first() // 단일 외화
                else -> { // 다중 외화
                    val paidInForeignCurrency = allExpenses
                        .filter { it.currency != KRW && it.currency != null }
                        .groupBy { it.currency!!.uppercase() }
                        .mapValues { (_, expenses) -> expenses.sumOf { it.totalAmount } }
                    paidInForeignCurrency.maxByOrNull { it.value }?.key
                        ?: trip.country.code.uppercase()
                }
            }

            val singleDebtExchangeRate = if (singleDebtCurrencyCode != KRW) {
                // findClosestRate는 이 경우에 부적합하므로, findTopByCurUnitOrderByDateDesc를 사용
                currencyRepository.findTopByCurUnitOrderByDateDesc(singleDebtCurrencyCode)?.exchangeRate
                    ?: throw BusinessException(ErrorCode.EXCHANGE_RATE_NOT_FOUND)
            } else {
                BigDecimal.ONE
            }

            // 2.2. 원래의 최소 송금 알고리즘 호출
            val finalBalances = balancesForDebtCalc.map { it.first to it.second } // Pair<TripMember, BigDecimal>
            debtRelations.addAll(
                calculateDebtRelations(
                    finalBalances,
                    singleDebtCurrencyCode,
                    singleDebtExchangeRate
                )
            )
        }


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
     * 두 멤버 간의 1:1 부채 관계를 외화별로 분리하여 최소 송금 관계를 계산합니다.
     * 이 함수는 Daily Settlement (date-based rate)와 Total Settlement (latest rate)에서 모두 사용됩니다.
     *
     * @param balances 2명의 TripMember와 KRW 잔액을 포함하는 목록 (Pair<TripMember, BigDecimal>)
     * @param expenses 정산에 사용될 지출 목록 (DailyExpenses 또는 AllExpenses)
     * @param rateLookup 환율을 조회하는 함수 (통화 코드 -> 환율 BigDecimal?)
     * @return 통화별로 분리된 최소 송금 관계 목록
     */
    private fun calculateOneToOneDebtRelations(
        balances: List<Pair<TripMember, BigDecimal>>,
        expenses: List<Expense>,
        rateLookup: (currencyCode: String) -> BigDecimal?
    ): List<SettlementDto.DebtRelation> {

        // 1. Debtor와 Creditor 식별
        val debtorPair = balances.minByOrNull { it.second }!!
        val creditorPair = balances.maxByOrNull { it.second }!!
        val debtorMember = debtorPair.first
        val creditorMember = creditorPair.first
        val debtorNetBalance = debtorPair.second.abs() // 최종 송금해야 할 KRW 금액 (KRW 부채)

        // 2. 외화별 순 부채 구성 요소 계산 (Debtor가 외화로 '분담받은 총액' - '결제한 총액')
        val foreignDebtComponentsKrw = expenses
            .filter { it.currency != KRW && it.currency != null }
            .groupBy { it.currency!!.uppercase() }
            .mapValues { (_, expensesInCurrency) ->
                // 해당 통화로 Debtor가 분담받은 총액 (Assigned_F)
                val assignedKrw = expensesInCurrency
                    .flatMap { expense -> expense.expenseItems }
                    .flatMap { item -> item.assignments }
                    .distinct()
                    .filter { it.tripMember.id == debtorMember.id }
                    .sumOf { assignment ->
                        val expense = assignment.expenseItem.expense
                        convertToKrw(assignment.amount, expense)
                    }

                // 해당 통화로 Debtor가 결제한 총액 (Paid_F)
                val paidKrw = expensesInCurrency
                    .filter { it.payer.id == debtorMember.id }
                    .sumOf { expense -> convertToKrw(expense.totalAmount, expense) }

                // 순 외화 부채 = max(0, Assigned_F - Paid_F)
                assignedKrw.subtract(paidKrw).max(BigDecimal.ZERO)
            }
            .filterValues { it.compareTo(BigDecimal.ZERO) > 0 } // 양수 순 부채만 포함

        // [FIX: 비례 배분 로직]

        // 1. 모든 순 외화 부채의 합산 (KRW 기준)
        val sumNetForeignDebtKrw = foreignDebtComponentsKrw.values.sumOf { it }

        // 2. 전체 부채 대비 외화 부채의 비율을 계산하고, 비율 적용이 필요한지 판단
        val debtorNetBalanceAbs = debtorNetBalance.abs()
        val isScalingNeeded = sumNetForeignDebtKrw.compareTo(debtorNetBalanceAbs) > 0

        val scaleFactor = if (isScalingNeeded && sumNetForeignDebtKrw.compareTo(BigDecimal.ZERO) > 0) {
            // 외화 부채의 합이 전체 부채를 초과하면 비율로 축소
            debtorNetBalanceAbs.divide(sumNetForeignDebtKrw, 4, RoundingMode.HALF_UP) // SCALE=4로 정밀하게 계산
        } else {
            BigDecimal.ONE
        }

        // 3. 각 통화별로 송금 관계 DTO 생성
        val debtRelations = mutableListOf<SettlementDto.DebtRelation>()
        var totalForeignDebtKrw = BigDecimal.ZERO

        for ((currencyCode, netForeignDebtKrw) in foreignDebtComponentsKrw) {

            // 3.1. 비율에 따라 실제 송금액 결정 (KRW 기준)
            val actualTransferAmountKrw = netForeignDebtKrw.multiply(scaleFactor)
                .setScale(SCALE, RoundingMode.HALF_UP) // 정산은 원화 단위(0)로 처리

            // EPSILON 미만(1원 미만)은 무시하고, 마지막 항목에서 KRW 잔액으로 처리하도록 함
            if (actualTransferAmountKrw.compareTo(EPSILON) < 0) continue

            totalForeignDebtKrw = totalForeignDebtKrw.add(actualTransferAmountKrw)

            // 3.2. 환율 조회 (rateLookup 함수 사용)
            val rate = rateLookup(currencyCode)
                ?: continue

            // 3.3. KRW 송금액을 해당 통화로 역산 (FOREIGN_CURRENCY_SCALE 적용)
            val equivalentOriginalAmount = actualTransferAmountKrw.divide(rate, FOREIGN_CURRENCY_SCALE, RoundingMode.HALF_UP)

            debtRelations.add(
                SettlementDto.DebtRelation(
                    fromNickname = debtorMember.name,
                    fromTripMemberId = debtorMember.id!!,
                    toNickname = creditorMember.name,
                    toTripMemberId = creditorMember.id!!,
                    amount = actualTransferAmountKrw, // KRW 송금 금액
                    equivalentOriginalAmount = equivalentOriginalAmount,
                    originalCurrencyCode = currencyCode
                )
            )
        }

        // 4. KRW 부채 처리
        val remainingKrwDebt = debtorNetBalance.subtract(totalForeignDebtKrw)

        if (remainingKrwDebt.compareTo(BigDecimal.ZERO) > 0) {
            // KRW 순 부채가 남은 경우, KRW로 송금 관계를 추가합니다.
            debtRelations.add(
                SettlementDto.DebtRelation(
                    fromNickname = debtorMember.name,
                    fromTripMemberId = debtorMember.id!!,
                    toNickname = creditorMember.name,
                    toTripMemberId = creditorMember.id!!,
                    amount = remainingKrwDebt,
                    equivalentOriginalAmount = null,
                    originalCurrencyCode = null
                )
            )
        }
        return debtRelations
    }

    /**
     * 채권/채무 관계를 계산하여 최소 송금 관계로 변환합니다. (Greedy Algorithm)
     * 이 함수는 이제 다자간 정산이 필요한 경우에 사용됩니다.
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
                transferAmount.divide(assumedExchangeRate, FOREIGN_CURRENCY_SCALE, RoundingMode.HALF_UP)
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