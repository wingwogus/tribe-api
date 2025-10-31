package com.tribe.tribe_api.expense.service

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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
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
    private val expenseRepository: ExpenseRepository
) {
    private lateinit var trip: Trip
    private lateinit var memberA: TripMember
    private lateinit var memberB: TripMember
    private lateinit var guestC: TripMember
    private val paymentDate = LocalDate.of(2025, 10, 26)

    @BeforeEach
    fun setUp() {
        // 1. 사용자 생성
        val userA = memberRepository.save(Member("settlement.a@test.com", passwordEncoder.encode("pw"), "정산맨A", null, Role.USER, Provider.LOCAL, null, false))
        val userB = memberRepository.save(Member("settlement.b@test.com", passwordEncoder.encode("pw"), "정산맨B", null, Role.USER, Provider.LOCAL, null, false))

        // 2. 여행 데이터 생성
        trip = Trip("정산 테스트 여행", LocalDate.now(), LocalDate.now().plusDays(5), Country.SOUTH_KOREA)
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

        // 4. 테스트용 지출 데이터 생성
        val dinnerExpense = Expense(trip, itinerary, memberA, "저녁 식사", BigDecimal(30000), InputMethod.HANDWRITE, paymentDate)
        val dinnerItem = ExpenseItem(dinnerExpense, "저녁메뉴", BigDecimal(30000))
        dinnerExpense.expenseItems.add(dinnerItem)
        dinnerItem.assignments.add(ExpenseAssignment(dinnerItem, memberA, BigDecimal(15000)))
        dinnerItem.assignments.add(ExpenseAssignment(dinnerItem, memberB, BigDecimal(15000)))
        expenseRepository.save(dinnerExpense)

        val snackExpense = Expense(trip, itinerary, memberB, "간식", BigDecimal(12000), InputMethod.HANDWRITE, paymentDate)
        val snackItem = ExpenseItem(snackExpense, "간식메뉴", BigDecimal(12000))
        snackExpense.expenseItems.add(snackItem)
        snackItem.assignments.add(ExpenseAssignment(snackItem, memberA, BigDecimal(4000)))
        snackItem.assignments.add(ExpenseAssignment(snackItem, memberB, BigDecimal(4000)))
        snackItem.assignments.add(ExpenseAssignment(snackItem, guestC, BigDecimal(4000)))
        expenseRepository.save(snackExpense)
    }

    @Test
    @DisplayName("일별 정산 조회 성공")
    fun getDailySettlement_Success() {
        // when
        val response = settlementService.getDailySettlement(trip.id!!, paymentDate)

        // then
        // 1. 기본 정보 및 멤버 요약 검증 (기존 로직)
        assertThat(response.date).isEqualTo(paymentDate)
        assertThat(response.dailyTotalAmount).isEqualByComparingTo(BigDecimal(42000))
        assertThat(response.expenses).hasSize(2)

        val summaryA = response.memberSummaries.first { it.memberName == "정산맨A" }
        val summaryB = response.memberSummaries.first { it.memberName == "정산맨B" }
        val summaryC = response.memberSummaries.first { it.memberName == "게스트C" }

        // Paid/Assigned 금액 검증
        assertThat(summaryA.paidAmount).isEqualByComparingTo(BigDecimal(30000))
        assertThat(summaryA.assignedAmount).isEqualByComparingTo(BigDecimal(19000)) // 잔액: +11000

        assertThat(summaryB.paidAmount).isEqualByComparingTo(BigDecimal(12000))
        assertThat(summaryB.assignedAmount).isEqualByComparingTo(BigDecimal(19000)) // 잔액: -7000

        assertThat(summaryC.paidAmount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(summaryC.assignedAmount).isEqualByComparingTo(BigDecimal(4000)) // 잔액: -4000

        // 2. 최소 송금 관계(debtRelations) 검증 (추가된 로직)
        assertThat(response.debtRelations).hasSize(2)

        // 정산맨B (채무자 -7000) -> 정산맨A (채권자 +11000)
        val debtBtoA = response.debtRelations.first { it.fromNickname == "정산맨B" }
        // 게스트C (채무자 -4000) -> 정산맨A (채권자 +4000)
        val debtCtoA = response.debtRelations.first { it.fromNickname == "게스트C" }

        // 정산맨B -> 정산맨A : 7,000
        assertThat(debtBtoA.toNickname).isEqualTo("정산맨A")
        assertThat(debtBtoA.amount.toInt()).isEqualTo(7000)

        // 게스트C -> 정산맨A : 4,000
        assertThat(debtCtoA.toNickname).isEqualTo("정산맨A")
        assertThat(debtCtoA.amount.toInt()).isEqualTo(4000)
    }

    @Test
    @DisplayName("전체 정산 조회 및 최소 송금 관계 계산 성공")
    fun getTotalSettlement_Success() {
        // when
        val response = settlementService.getTotalSettlement(trip.id!!)

        // then
        val balanceA = response.memberBalances.first { it.nickname == "정산맨A" }
        val balanceB = response.memberBalances.first { it.nickname == "정산맨B" }
        val balanceC = response.memberBalances.first { it.nickname == "게스트C" }

        assertThat(balanceA.balance.toInt()).isEqualTo(11000)
        assertThat(balanceB.balance.toInt()).isEqualTo(-7000)
        assertThat(balanceC.balance.toInt()).isEqualTo(-4000)

        assertThat(response.debtRelations).hasSize(2)
        val debtBtoA = response.debtRelations.first { it.fromNickname == "정산맨B" }
        val debtCtoA = response.debtRelations.first { it.fromNickname == "게스트C" }

        assertThat(debtBtoA.toNickname).isEqualTo("정산맨A")
        assertThat(debtBtoA.amount.toInt()).isEqualTo(7000)
        assertThat(debtCtoA.toNickname).isEqualTo("정산맨A")
        assertThat(debtCtoA.amount.toInt()).isEqualTo(4000)
    }
}