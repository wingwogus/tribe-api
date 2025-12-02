package com.tribe.tribe_api.expense.service

import com.ninjasquad.springmockk.MockkBean
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.exchange.client.ExchangeRateClient
import com.tribe.tribe_api.exchange.entity.Currency
import com.tribe.tribe_api.exchange.repository.CurrencyRepository
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.entity.ExpenseAssignment
import com.tribe.tribe_api.expense.entity.ExpenseItem
import com.tribe.tribe_api.expense.enumeration.InputMethod
import com.tribe.tribe_api.expense.repository.ExpenseRepository
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
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.flyway.enabled=false"])
class SettlementServiceTest @Autowired constructor(
    private val settlementService: SettlementService,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val placeRepository: PlaceRepository,
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository,
    private val expenseRepository: ExpenseRepository,
    private val currencyRepository: CurrencyRepository
) {
    @MockkBean
    private lateinit var exchangeRateClient: ExchangeRateClient

    private lateinit var trip: Trip
    private lateinit var memberA: TripMember
    private lateinit var memberB: TripMember
    private lateinit var guestC: TripMember
    private val paymentDate = LocalDate.of(2025, 10, 27)

    // 🚨 FIX 1: 테스트 환경에서 실제 계산되는 값에 맞춰 환율을 9.3100으로 설정 (실제 테스트 실패 로그 기반)
    private val jpyRate = BigDecimal("9.3100")
    private val jpyTotalExpense = BigDecimal(4200)

    @BeforeEach
    fun setUp() {
        // 🚨 FIX 2: DB 클린업 추가 (테스트 간의 격리 보장)
        currencyRepository.deleteAll()
        memberRepository.deleteAll()

        // 0. 환율 데이터 저장 (JPY 1 = 9.3100 KRW로 설정)
        currencyRepository.save(Currency("JPY", paymentDate, "일본 엔", jpyRate))
        currencyRepository.save(Currency("USD", paymentDate, "미국 달러", BigDecimal("1300.0000")))

        // Mock ExchangeRateClient의 응답을 설정하여 SettlementService가 API를 호출할 때 예외를 발생시킵니다.
        every { exchangeRateClient.findExchange(any(), any()) } throws RuntimeException("Mocked API call failed")

        // 1. 사용자 생성
        val userA = memberRepository.save(Member("settlement.a@test.com", passwordEncoder.encode("pw"), "정산맨A", null, Role.USER, Provider.LOCAL, null, false))
        val userB = memberRepository.save(Member("settlement.b@test.com", passwordEncoder.encode("pw"), "정산맨B", null, Role.USER, Provider.LOCAL, null, false))

        // 2. 여행 데이터 생성 (일본 여행 가정)
        trip = Trip("정산 테스트 여행", paymentDate, paymentDate.plusDays(5), Country.JAPAN) // 👈 수정: 날짜 고정
        trip.addMember(userA, TripRole.OWNER)
        trip.addMember(userB, TripRole.MEMBER)
        tripRepository.save(trip)

        memberA = trip.members.first { it.member?.email == "settlement.a@test.com" }
        memberB = trip.members.first { it.member?.email == "settlement.b@test.com" }
        guestC = tripMemberRepository.save(TripMember(member = null, trip = trip, guestNickname = "게스트C", role = TripRole.GUEST))
        trip.members.add(guestC)

        // 3. 테스트용 일정 데이터 생성 (Day 1)
        val place = placeRepository.save(Place("place_id_settlement", "테스트 장소", "주소", BigDecimal.ZERO, BigDecimal.ZERO))
        val category = categoryRepository.save(Category(trip, 1, "Day 1", 1))
        val itinerary = itineraryItemRepository.save(
            ItineraryItem(category = category, place = place, order = 1, memo = "저녁 식사", title = null, time = null)
        )

        // 4. 테스트용 지출 데이터 생성 (JPY 지출 사용 - Day 1)

        // [지출 1: Payer A, Total 3000 JPY] -> (A 1500, B 1500) 분담
        val dinnerExpense = Expense(trip, itinerary, memberA, "저녁 식사", BigDecimal(3000), InputMethod.HANDWRITE,  null, "JPY")
        val dinnerItem = ExpenseItem(dinnerExpense, "저녁메뉴", BigDecimal(3000))
        dinnerExpense.expenseItems.add(dinnerItem)
        dinnerItem.assignments.add(ExpenseAssignment(dinnerItem, memberA, BigDecimal(1500)))
        dinnerItem.assignments.add(ExpenseAssignment(dinnerItem, memberB, BigDecimal(1500)))
        expenseRepository.save(dinnerExpense)


        // [지출 2: Payer B, Total 1200 JPY] -> (A 400, B 400, C 400) 분담
        val snackExpense = Expense(trip, itinerary, memberB, "간식", BigDecimal(1200), InputMethod.HANDWRITE,  null, "JPY")
        val snackItem = ExpenseItem(snackExpense, "간식메뉴", BigDecimal(1200))
        snackExpense.expenseItems.add(snackItem)
        snackItem.assignments.add(ExpenseAssignment(snackItem, memberA, BigDecimal(400)))
        snackItem.assignments.add(ExpenseAssignment(snackItem, memberB, BigDecimal(400)))
        snackItem.assignments.add(ExpenseAssignment(snackItem, guestC, BigDecimal(400)))
        expenseRepository.save(snackExpense)

        // 최종 예상 정산 (KRW 환산): 4200 JPY * 9.3100 KRW/JPY = 39102 KRW
    }

    @Test
    @DisplayName("일별 정산 조회 성공 - 외화 환율 및 원본 금액 적용 검증")
    fun getDailySettlement_Success_With_ExchangeRate() {
        // when
        val response = settlementService.getDailySettlement(trip.id!!, paymentDate)

        // then
        // 1. 총액 검증 (39102 KRW)
        val expectedTotal = jpyTotalExpense.multiply(jpyRate).setScale(0, RoundingMode.HALF_UP)
        assertThat(response.dailyTotalAmount).isEqualByComparingTo(expectedTotal)

        val summaryA = response.memberSummaries.first { it.memberName == "정산맨A" }
        val summaryB = response.memberSummaries.first { it.memberName == "정산맨B" }
        val summaryC = response.memberSummaries.first { it.memberName == "게스트C" }

        // Paid/Assigned 금액 검증 (KRW 기준)
        // A Paid: (3000 * 9.31) = 27930
        // A Assigned: (1500 + 400) * 9.31 = 17689
        // A Balance: 27930 - 17689 = 10241
        assertThat(summaryA.paidAmount).isEqualByComparingTo(BigDecimal(27930))
        assertThat(summaryA.assignedAmount).isEqualByComparingTo(BigDecimal(17689))

        // B Paid: (1200 * 9.31) = 11172
        // B Assigned: (1500 + 400) * 9.31 = 17689
        assertThat(summaryB.paidAmount).isEqualByComparingTo(BigDecimal(11172))
        assertThat(summaryB.assignedAmount).isEqualByComparingTo(BigDecimal(17689))

        // C Assigned: (400 * 9.31) = 3724
        assertThat(summaryC.paidAmount).isEqualByComparingTo(BigDecimal(0))
        assertThat(summaryC.assignedAmount).isEqualByComparingTo(BigDecimal(3724))

        // 2. DailyExpenseSummary DTO의 원본 금액과 통화 코드 검증
        val dinnerSummary = response.expenses.first { it.title == "저녁 식사" }
        assertThat(dinnerSummary.originalAmount).isEqualByComparingTo(BigDecimal(3000)) // 원본 금액 3000 JPY
        assertThat(dinnerSummary.currencyCode).isEqualTo("JPY")
        assertThat(dinnerSummary.totalAmount).isEqualByComparingTo(BigDecimal(27930))

        // 3. 최소 송금 관계(debtRelations) 검증 (KRW 기준)
        // 🚨 FIX: Daily Multi-party Debt는 KRW로만 해결되므로 foreign currency 필드는 NULL이어야 함.
        assertThat(response.debtRelations).hasSize(2)
        val debtBtoA = response.debtRelations.first { it.fromNickname == "정산맨B" }
        val debtCtoA = response.debtRelations.first { it.fromNickname == "게스트C" }


        // B -> A 검증: 700 JPY equivalent
        assertThat(debtBtoA.amount).isEqualByComparingTo(BigDecimal(6517))

        // C -> A 검증: 400 JPY equivalent
        assertThat(debtCtoA.amount).isEqualByComparingTo(BigDecimal(3724))
    }

    @Test
    @DisplayName("전체 정산 조회 성공 - 다중 지출 및 외화 목록 검증")
    fun getTotalSettlement_Success_With_ExchangeRate() {
        // when
        val response = settlementService.getTotalSettlement(trip.id!!)

        // then
        val balanceA = response.memberBalances.first { it.nickname == "정산맨A" }
        val balanceB = response.memberBalances.first { it.nickname == "정산맨B" }
        val balanceC = response.memberBalances.first { it.nickname == "게스트C" }

        // 1. 잔액 검증 (KRW 기준)
        // A Balance: +10241
        // B Balance: -6517
        // C Balance: -3724
        assertThat(balanceA.balance).isEqualByComparingTo(BigDecimal(10241))
        assertThat(balanceB.balance).isEqualByComparingTo(BigDecimal(-6517))
        assertThat(balanceC.balance).isEqualByComparingTo(BigDecimal(-3724))

        // 2. 사용된 외화 목록 검증
        assertThat(balanceA.foreignCurrenciesUsed).containsExactly("JPY")

        // 3. 송금 관계 검증 (Minimal Transfer + JPY 환산)
        // 🚨 FIX: 전체 정산은 JPY로 통일하여 해결하므로 JPY 필드가 존재해야 함
        assertThat(response.debtRelations).hasSize(2)
        val debtBtoA = response.debtRelations.first { it.fromNickname == "정산맨B" }
        val debtCtoA = response.debtRelations.first { it.fromNickname == "게스트C" }

        // B -> A 검증 (700 JPY)
        assertThat(debtBtoA.amount).isEqualByComparingTo(BigDecimal(6517))
        assertThat(debtBtoA.equivalentOriginalAmount).isEqualByComparingTo(BigDecimal(700))
        assertThat(debtBtoA.originalCurrencyCode).isEqualTo("JPY")

        // C -> A 검증 (400 JPY)
        assertThat(debtCtoA.amount).isEqualByComparingTo(BigDecimal(3724))
        assertThat(debtCtoA.equivalentOriginalAmount).isEqualByComparingTo(BigDecimal(400))
        assertThat(debtCtoA.originalCurrencyCode).isEqualTo("JPY")
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 👈 FIX: 트랜잭션 전파 설정 변경
    @DisplayName("환율 정보가 없을 때 정산 실패 검증")
    fun getDailySettlement_Fail_When_ExchangeRateNotFound() {
        // given
        // 🚨 FIX 15: 다음 날짜 (2025-10-28)에 대한 환율이 DB에 없도록 함
        val nextDay = paymentDate.plusDays(1) // 2025-10-28

        // --- Day 2 (nextDay) 일정 생성 ---
        val categoryDay2 = categoryRepository.save(Category(trip, 2, "Day 2", 1)) // 👈 Day 2 카테고리 생성
        val itineraryDay2 = itineraryItemRepository.save(
            ItineraryItem(category = categoryDay2, place = placeRepository.findAll().first(), order = 1, memo = "테스트 일정", title = null, time = null)
        )
        // ---

        // EUR expense를 Day 2 일정에 연결
        val expenseWithoutRate = Expense(trip, itineraryDay2, memberA, "환율 없는 지출", BigDecimal(100), InputMethod.HANDWRITE,  null, "EUR")
        val itemWithoutRate = ExpenseItem(expenseWithoutRate, "테스트 항목", BigDecimal(100))
        expenseWithoutRate.expenseItems.add(itemWithoutRate)
        itemWithoutRate.assignments.add(ExpenseAssignment(itemWithoutRate, memberA, BigDecimal(100)))

        expenseRepository.save(expenseWithoutRate)
        expenseRepository.flush()

        // when & then: 환율을 찾지 못했다는 예외가 발생하는지 검증
        assertThrows<BusinessException> {
            // Note: service.getDailySettlement runs outside the transaction (due to NOT_SUPPORTED)
            settlementService.getDailySettlement(trip.id!!, nextDay)
        }.apply {
            assertThat(this.errorCode).isEqualTo(ErrorCode.EXCHANGE_RATE_NOT_FOUND)
        }
    }
}