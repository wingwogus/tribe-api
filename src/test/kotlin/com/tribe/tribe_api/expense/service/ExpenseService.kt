package com.tribe.tribe_api.expense.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.expense.dto.ExpenseDto
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.entity.ExpenseAssignment
import com.tribe.tribe_api.expense.entity.ExpenseItem
import com.tribe.tribe_api.expense.enumeration.InputMethod
import com.tribe.tribe_api.expense.repository.ExpenseAssignmentRepository
import com.tribe.tribe_api.expense.repository.ExpenseRepository
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
import com.tribe.tribe_api.trip.repository.TripRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@Transactional
class ExpenseServiceTest @Autowired constructor(
    private val expenseService: ExpenseService,
    private val expenseRepository: ExpenseRepository,
    private val expenseAssignmentRepository: ExpenseAssignmentRepository,
    private val memberRepository: MemberRepository,
    private val tripRepository: TripRepository,
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository,
) {
    private lateinit var memberA: Member
    private lateinit var memberB: Member
    private lateinit var trip: Trip
    private lateinit var tripMemberA: TripMember
    private lateinit var tripMemberB: TripMember
    private lateinit var testExpense: Expense

    @BeforeEach
    fun setUp() {
        // 사용자 및 여행 데이터 설정
        memberA = memberRepository.save(Member("a@test.com", "pw", "멤버A", provider = Provider.LOCAL, role = Role.USER, isFirstLogin = false))
        memberB = memberRepository.save(Member("b@test.com", "pw", "멤버B", provider = Provider.LOCAL, role = Role.USER, isFirstLogin = false))
        trip = tripRepository.save(Trip("테스트 여행", LocalDate.now(), LocalDate.now().plusDays(1), Country.SOUTH_KOREA))
        trip.addMember(memberA, TripRole.OWNER)
        trip.addMember(memberB, TripRole.MEMBER)
        tripMemberA = trip.members.first { it.member?.id == memberA.id }
        tripMemberB = trip.members.first { it.member?.id == memberB.id }

        // 테스트용 지출 데이터 생성
        val category = categoryRepository.save(Category(trip, 1, "식사", 1))
        val itineraryItem = itineraryItemRepository.save(ItineraryItem(category, null, 1, "저녁"))

        val initialExpense = Expense(
            trip = trip,
            itineraryItem = itineraryItem,
            payer = tripMemberA,
            title = "초기 지출",
            totalAmount = BigDecimal(10000),
            entryMethod = InputMethod.HANDWRITE,
            paymentDate = LocalDate.now()
        )
        val item1 = ExpenseItem(initialExpense, "항목1", BigDecimal(6000))
        val item2 = ExpenseItem(initialExpense, "항목2", BigDecimal(4000))

        // 초기 배분 데이터 설정
        item1.assignments.add(ExpenseAssignment(item1, tripMemberA, BigDecimal(6000)))
        item2.assignments.add(ExpenseAssignment(item2, tripMemberB, BigDecimal(4000)))

        initialExpense.addExpenseItem(item1)
        initialExpense.addExpenseItem(item2)

        testExpense = expenseRepository.save(initialExpense)
    }

    @Test
    @DisplayName("지출 수정 성공 - 금액 합계가 일치하는 경우")
    fun updateExpense_Success_WhenAmountMatches() {
        // given
        val item1Id = testExpense.expenseItems[0].id!!
        val item2Id = testExpense.expenseItems[1].id!!

        val updateRequest = ExpenseDto.UpdateRequest(
            tripId = trip.id!!,
            expenseTitle = "수정된 지출",
            totalAmount = BigDecimal(20000), // 아이템 합계와 동일
            paymentDate = LocalDate.now().minusDays(1),
            payerId = tripMemberB.id!!, // 결제자 변경
            items = listOf(
                ExpenseDto.ItemUpdate(item1Id, "수정된 항목1", BigDecimal(12000)),
                ExpenseDto.ItemUpdate(item2Id, "수정된 항목2", BigDecimal(8000))
            )
        )

        val initialAssignmentCount = expenseAssignmentRepository.findAll().count { it.expenseItem.expense.id == testExpense.id }
        assertThat(initialAssignmentCount).isEqualTo(2) // 수정 전 배분 내역 2개 확인

        // when
        expenseService.updateExpense(trip.id!!, testExpense.id!!, updateRequest)

        // then
        val updatedExpense = expenseRepository.findById(testExpense.id!!).get()
        assertThat(updatedExpense.title).isEqualTo("수정된 지출")
        assertThat(updatedExpense.totalAmount.toInt()).isEqualTo(20000)
        assertThat(updatedExpense.payer.id).isEqualTo(tripMemberB.id!!)
        assertThat(updatedExpense.expenseItems).hasSize(2)
        assertThat(updatedExpense.expenseItems[0].price.toInt()).isEqualTo(12000)

        // **핵심 검증**: 수정 후 기존 배분 내역이 모두 삭제되었는지 확인
        val finalAssignmentCount = expenseAssignmentRepository.findAll().count { it.expenseItem.expense.id == testExpense.id }
        assertThat(finalAssignmentCount).isEqualTo(0)
    }

    @Test
    @DisplayName("지출 수정 실패 - 총액과 아이템 합계가 불일치하는 경우")
    fun updateExpense_Fail_WhenAmountMismatches() {
        // given
        val item1Id = testExpense.expenseItems[0].id!!
        val item2Id = testExpense.expenseItems[1].id!!

        val updateRequest = ExpenseDto.UpdateRequest(
            tripId = trip.id!!,
            expenseTitle = "실패할 수정",
            totalAmount = BigDecimal(15000), // 아이템 합계(20000)와 다름
            paymentDate = LocalDate.now(),
            payerId = tripMemberA.id!!,
            items = listOf(
                ExpenseDto.ItemUpdate(item1Id, "항목1", BigDecimal(12000)),
                ExpenseDto.ItemUpdate(item2Id, "항목2", BigDecimal(8000))
            )
        )

        // when & then
        val exception = assertThrows<BusinessException> {
            expenseService.updateExpense(trip.id!!, testExpense.id!!, updateRequest)
        }

        assertThat(exception.errorCode).isEqualTo(ErrorCode.EXPENSE_TOTAL_AMOUNT_MISMATCH)

        // 원본 데이터가 변경되지 않았는지 확인
        val originalExpense = expenseRepository.findById(testExpense.id!!).get()
        assertThat(originalExpense.title).isEqualTo("초기 지출")
        assertThat(originalExpense.totalAmount.toInt()).isEqualTo(10000)
    }
}