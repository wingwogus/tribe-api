package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.entity.ExpenseAssignment
import com.tribe.tribe_api.expense.entity.ExpenseItem
import com.tribe.tribe_api.expense.enumeration.InputMethod
import com.tribe.tribe_api.expense.repository.ExpenseRepository
import com.tribe.tribe_api.exchange.entity.Currency
import com.tribe.tribe_api.exchange.repository.CurrencyRepository // 💡 추가: 환율 Repository
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.itinerary.repository.ItineraryItemRepository
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@Transactional
class SettlementServiceIntegrationTest @Autowired constructor(
    private val settlementService: SettlementService,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val placeRepository: PlaceRepository,
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository,
    private val expenseRepository: ExpenseRepository,
    private val currencyRepository: CurrencyRepository // 💡 CurrencyRepository 주입
) {
    private lateinit var trip: Trip
    private lateinit var memberA: TripMember
    private lateinit var memberB: TripMember
    private lateinit var guestC: TripMember
    private val paymentDate = LocalDate.of(2025, 10, 26)

    // 테스트용 환율 (1 JPY = 10 KRW)
    private val JPY_RATE = BigDecimal("10.0000")

    @BeforeEach
    fun setUp() {
        // 0. 환율 데이터 저장 (테스트 정산의 기반)
        currencyRepository.save(Currency("JPY", "일본 엔", JPY_RATE, paymentDate))
        currencyRepository.save(Currency("USD", "미국 달러", BigDecimal("1300.0000"), paymentDate))


        // 1. 사용자 생성
        val userA = memberRepository.save(Member("settlement.a@test.com", passwordEncoder.encode("pw"), "정산맨A", null, Role.USER, Provider.LOCAL, null, false))
        val userB = memberRepository.save(Member("settlement.b@test.com", passwordEncoder.encode("pw"), "정산맨B", null, Role.USER, Provider.LOCAL, null, false))

        // 2. 여행 데이터 생성 (일본 여행 가정)
        trip = Trip("정산 테스트 여행", LocalDate.now(), LocalDate.now().plusDays(5), Country.JAPAN) // 💡 국가 JAPAN으로 변경
        trip.addMember(userA, TripRole.OWNER)
        trip.addMember(userB, TripRole.MEMBER)
        tripRepository.save(trip)

        memberA = trip.members.first { it.member?.email == "settlement.a@test.com" }
        memberB = trip.members.first { it.member?.email == "settlement.b@test.com" }
        guestC = tripMemberRepository.save(TripMember(member = null, trip = trip, guestNickname = "게스트C", role = TripRole.GUEST))
        trip.members.add(guestC)

        // 3. 테스트용 일정 데이터 생성
        val place = placeRepository.save(Place("place_id_settlement", "테스트 장소", "주소", BigDecimal.ZERO, BigDecimal.ZERO))
        val category = categoryRepository.save(Category(trip, 1, "Day 1", 1))

        val itinerary = itineraryItemRepository.save(
            ItineraryItem(
                category = category,
                place = place,
                order = 1,
                memo = "저녁 식사",
                title = null,
                time = null
            )
        )

        // 4. 테스트용 지출 데이터 생성 (JPY 지출 사용)
        // JPY 지출 1: Payer A, Total 3000 JPY (A 1500, B 1500) -> 30,000 KRW
        // JPY 지출 2: Payer B, Total 1200 JPY (A 400, B 400, C 400) -> 12,000 KRW

        val dinnerExpense = Expense(trip, itinerary, memberA, "저녁 식사", BigDecimal(3000), InputMethod.HANDWRITE, paymentDate, "JPY") // 💡 JPY 지출
        val dinnerItem = ExpenseItem(dinnerExpense, "저녁메뉴", BigDecimal(3000))
        dinnerExpense.expenseItems.add(dinnerItem)
        dinnerItem.assignments.add(ExpenseAssignment(dinnerItem, memberA, BigDecimal(1500)))
        dinnerItem.assignments.add(ExpenseAssignment(dinnerItem, memberB, BigDecimal(1500)))
        expenseRepository.save(dinnerExpense)

        val snackExpense = Expense(trip, itinerary, memberB, "간식", BigDecimal(1200), InputMethod.HANDWRITE, paymentDate, "JPY") // 💡 JPY 지출
        val snackItem = ExpenseItem(snackExpense, "간식메뉴", BigDecimal(1200))
        snackExpense.expenseItems.add(snackItem)
        snackItem.assignments.add(ExpenseAssignment(snackItem, memberA, BigDecimal(400)))
        snackItem.assignments.add(ExpenseAssignment(snackItem, memberB, BigDecimal(400)))
        snackItem.assignments.add(ExpenseAssignment(snackItem, guestC, BigDecimal(400)))
        expenseRepository.save(snackExpense)

        // 환산 결과: Balance A: +11,000 KRW, Balance B: -7,000 KRW, Balance C: -4,000 KRW
    }

    @Test
    @DisplayName("일별 정산 조회 성공 - 외화 환율 및 원본 금액 적용 검증")
    fun getDailySettlement_Success_With_ExchangeRate() {
        // when
        val response = settlementService.getDailySettlement(trip.id!!, paymentDate)

        // then
        // 1. 총액 검증 (4200 JPY * 10 KRW/JPY = 42,000 KRW)
        assertThat(response.dailyTotalAmount).isEqualByComparingTo(BigDecimal(42000))

        val summaryA = response.memberSummaries.first { it.memberName == "정산맨A" }
        val summaryB = response.memberSummaries.first { it.memberName == "정산맨B" }

        // Paid/Assigned 금액 검증 (KRW 기준)
        assertThat(summaryA.paidAmount).isEqualByComparingTo(BigDecimal(30000))  // 3000 JPY * 10
        assertThat(summaryB.assignedAmount).isEqualByComparingTo(BigDecimal(19000)) // 1900 JPY * 10

        // 2. DailyExpenseSummary DTO의 원본 금액과 통화 코드 검증
        val dinnerSummary = response.expenses.first { it.title == "저녁 식사" }
        assertThat(dinnerSummary.originalAmount).isEqualByComparingTo(BigDecimal(3000)) // 💡 원본 금액 3000 JPY
        assertThat(dinnerSummary.currencyCode).isEqualTo("JPY")
        assertThat(dinnerSummary.totalAmount).isEqualByComparingTo(BigDecimal(30000)) // 💡 KRW 변환 금액

        // 3. 최소 송금 관계(debtRelations) 검증 (KRW 기준)
        assertThat(response.debtRelations).hasSize(2)
        val debtBtoA = response.debtRelations.first { it.fromNickname == "정산맨B" }
        assertThat(debtBtoA.amount).isEqualByComparingTo(BigDecimal(7000)) // 7,000 KRW
    }

    @Test
    @DisplayName("전체 정산 조회 성공 - 외화 환율 적용 및 사용된 외화 목록 검증")
    fun getTotalSettlement_Success_With_ExchangeRate() {
        // when
        val response = settlementService.getTotalSettlement(trip.id!!)

        // then
        val balanceA = response.memberBalances.first { it.nickname == "정산맨A" }
        val balanceB = response.memberBalances.first { it.nickname == "정산맨B" }
        val balanceC = response.memberBalances.first { it.nickname == "게스트C" }

        // 1. 잔액 검증 (KRW 기준)
        assertThat(balanceA.balance).isEqualByComparingTo(BigDecimal(11000))
        assertThat(balanceB.balance).isEqualByComparingTo(BigDecimal(-7000))
        assertThat(balanceC.balance).isEqualByComparingTo(BigDecimal(-4000))

        // 2. 사용된 외화 목록 검증
        assertThat(balanceA.foreignCurrenciesUsed).containsExactly("JPY")
        assertThat(balanceB.foreignCurrenciesUsed).containsExactly("JPY")
        assertThat(balanceC.foreignCurrenciesUsed).containsExactly("JPY")

        // 3. 송금 관계 검증
        assertThat(response.debtRelations).hasSize(2)
    }

    @Test
    @DisplayName("환율 정보가 없을 때 정산 실패 검증")
    fun getDailySettlement_Fail_When_ExchangeRateNotFound() {
        // given
        // 새로운 날짜 (2025-10-27)로 지출을 추가 (이 날짜에는 환율이 없음)
        val nextDay = paymentDate.plusDays(1)
        // 지출 ID 21의 여정 아이템을 사용 (DB에 존재)
        val itineraryItem = expenseRepository.findAll().first().itineraryItem
        val expenseWithoutRate = Expense(trip, itineraryItem, memberA, "환율 없는 지출", BigDecimal(100), InputMethod.HANDWRITE, nextDay, "USD")
        expenseRepository.save(expenseWithoutRate)

        // when & then
        val exception = assertThrows<BusinessException> {
            settlementService.getDailySettlement(trip.id!!, nextDay)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.EXCHANGE_RATE_NOT_FOUND) // 💡 환율 없음 예외 검증
    }
}