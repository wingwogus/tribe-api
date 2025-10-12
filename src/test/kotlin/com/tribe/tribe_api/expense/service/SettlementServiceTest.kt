package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.entity.ExpenseAssignment
import com.tribe.tribe_api.expense.entity.ExpenseItem
import com.tribe.tribe_api.expense.enumeration.InputMethod
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.itinerary.repository.ItineraryItemRepository
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
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository
) {
    private lateinit var memberA: Member
    private lateinit var memberB: Member
    private lateinit var trip: Trip
    private lateinit var tripMemberA: TripMember
    private lateinit var tripMemberB: TripMember
    private lateinit var guestC: TripMember
    private lateinit var itineraryItem: ItineraryItem

    private val settlementDate = LocalDate.of(2025, 10, 26)

    @BeforeEach
    fun setUp() {
        // 1. 사용자 생성
        memberA = memberRepository.save(
            Member("user1@example.com", passwordEncoder.encode("password"), "테스터A", null, Role.USER, Provider.LOCAL, null, false)
        )
        memberB = memberRepository.save(
            Member("user2@example.com", passwordEncoder.encode("password"), "테스터B", null, Role.USER, Provider.LOCAL, null, false)
        )

        // 2. 여행 및 멤버 설정
        trip = Trip("정산 테스트 여행", settlementDate, settlementDate.plusDays(3), Country.JAPAN)
        trip.addMember(memberA, TripRole.OWNER)
        trip.addMember(memberB, TripRole.MEMBER)
        tripRepository.save(trip)

        tripMemberA = trip.members.first { it.member?.id == memberA.id }
        tripMemberB = trip.members.first { it.member?.id == memberB.id }

        // 3. 게스트 추가
        guestC = tripMemberRepository.save(
            TripMember(member = null, trip = trip, guestNickname = "게스트C", role = TripRole.GUEST)
        )

        // 4. 테스트용 일정 생성
        val category = categoryRepository.save(Category(trip, 1, "식사", 1))
        itineraryItem = itineraryItemRepository.save(ItineraryItem(category, null, 1, "저녁 식사"))

        // 5. 지출 및 배분 데이터 생성
        setupExpenses()
    }

    private fun setupExpenses() {
        // 지출 1: 테스터A가 30,000원 결제
        val expense1 = Expense(trip, itineraryItem, tripMemberA, "저녁 식사", BigDecimal("30000"), InputMethod.HANDWRITE, settlementDate)
        val item1A = ExpenseItem(expense1, "라면과 맥주", BigDecimal("25000"))
        val item1B = ExpenseItem(expense1, "음료수", BigDecimal("5000"))

        // 배분: 라면/맥주는 A, B가 부담. 음료수는 C가 부담
        item1A.assignments.add(ExpenseAssignment(item1A, tripMemberA, BigDecimal("12500")))
        item1A.assignments.add(ExpenseAssignment(item1A, tripMemberB, BigDecimal("12500")))
        item1B.assignments.add(ExpenseAssignment(item1B, guestC, BigDecimal("5000")))
        expense1.expenseItems.addAll(listOf(item1A, item1B))

        // 지출 2: 테스터B가 10,000원 결제
        val expense2 = Expense(trip, itineraryItem, tripMemberB, "편의점 간식", BigDecimal("10000"), InputMethod.HANDWRITE, settlementDate)
        val item2A = ExpenseItem(expense2, "모두의 간식", BigDecimal("10000"))
        // 배분: A, B, C가 1/N 부담
        item2A.assignments.add(ExpenseAssignment(item2A, tripMemberA, BigDecimal("3333")))
        item2A.assignments.add(ExpenseAssignment(item2A, tripMemberB, BigDecimal("3334")))
        item2A.assignments.add(ExpenseAssignment(item2A, guestC, BigDecimal("3333")))
        expense2.expenseItems.add(item2A)

        trip.expenses.addAll(listOf(expense1, expense2))
        tripRepository.save(trip)
    }

    @Test
    @DisplayName("일별 정산 조회 성공")
    fun getDailySettlement_Success() {
        // when
        val dailyResponse = settlementService.getDailySettlement(trip.id!!, settlementDate)

        // then
        assertThat(dailyResponse.dailyTotalAmount.toInt()).isEqualTo(40000)
        assertThat(dailyResponse.expenses).hasSize(2)

        val summaryA = dailyResponse.memberSummaries.first { it.memberName == "테스터A" }
        val summaryB = dailyResponse.memberSummaries.first { it.memberName == "테스터B" }
        val summaryC = dailyResponse.memberSummaries.first { it.memberName == "게스트C" }

        assertThat(summaryA.paidAmount.toInt()).isEqualTo(30000)
        assertThat(summaryA.assignedAmount.toInt()).isEqualTo(12500 + 3333) // 15833

        assertThat(summaryB.paidAmount.toInt()).isEqualTo(10000)
        assertThat(summaryB.assignedAmount.toInt()).isEqualTo(12500 + 3334) // 15834

        assertThat(summaryC.paidAmount.toInt()).isEqualTo(0)
        assertThat(summaryC.assignedAmount.toInt()).isEqualTo(5000 + 3333) // 8333
    }

    @Test
    @DisplayName("전체 정산 조회 및 최소 송금 관계 계산 성공")
    fun getTotalSettlement_Success() {
        // when
        val totalResponse = settlementService.getTotalSettlement(trip.id!!)

        // then
        // 1. 최종 잔액 검증
        val balanceA = totalResponse.memberBalances.first { it.nickname == "테스터A" }
        val balanceB = totalResponse.memberBalances.first { it.nickname == "테스터B" }
        val balanceC = totalResponse.memberBalances.first { it.nickname == "게스트C" }

        // 30000(지출) - 15833(부담) = 14167
        assertThat(balanceA.balance.toInt()).isEqualTo(14167)
        // 10000(지출) - 15834(부담) = -5834
        assertThat(balanceB.balance.toInt()).isEqualTo(-5834)
        // 0(지출) - 8333(부담) = -8333
        assertThat(balanceC.balance.toInt()).isEqualTo(-8333)

        // 2. 송금 관계 검증 (Debtor -> Creditor)
        val debtRelations = totalResponse.debtRelations
        assertThat(debtRelations).hasSize(2)

        val debtFromB = debtRelations.first { it.fromNickname == "테스터B" }
        val debtFromC = debtRelations.first { it.fromNickname == "게스트C" }

        // B가 A에게 5834원 송금
        assertThat(debtFromB.toNickname).isEqualTo("테스터A")
        assertThat(debtFromB.amount.toInt()).isEqualTo(5834)

        // C가 A에게 8333원 송금
        assertThat(debtFromC.toNickname).isEqualTo("테스터A")
        assertThat(debtFromC.amount.toInt()).isEqualTo(8333)
    }
}