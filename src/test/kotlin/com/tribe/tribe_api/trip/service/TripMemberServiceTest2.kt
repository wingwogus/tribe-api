package com.tribe.tribe_api.trip.service

import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.expense.entity.Expense
import com.tribe.tribe_api.expense.entity.ExpenseAssignment
import com.tribe.tribe_api.expense.entity.ExpenseItem
import com.tribe.tribe_api.expense.enumeration.InputMethod
import com.tribe.tribe_api.expense.repository.ExpenseAssignmentRepository
import com.tribe.tribe_api.expense.repository.ExpenseItemRepository
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
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@Transactional
class TripMemberServiceTest2 @Autowired constructor(
    private val tripMemberService: TripMemberService,
    private val tripRepository: TripRepository,
    private val memberRepository: MemberRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val expenseRepository: ExpenseRepository,
    private val expenseItemRepository: ExpenseItemRepository,
    private val expenseAssignmentRepository: ExpenseAssignmentRepository,
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository
) {

    // 인증 정보 설정을 위한 헬퍼 메서드
    // SecurityUtil이 CustomUserDetails 타입을 요구하므로 Mock 객체를 주입합니다.
    private fun setAuthentication(memberId: Long) {
        // 1. Member 객체 Mocking
        val mockMember = Mockito.mock(Member::class.java)
        Mockito.`when`(mockMember.id).thenReturn(memberId)

        // 2. CustomUserDetails 객체 Mocking
        val mockUserDetails = Mockito.mock(CustomUserDetails::class.java)
        Mockito.`when`(mockUserDetails.member).thenReturn(mockMember)

        // 3. SecurityContext에 주입
        val authentication = UsernamePasswordAuthenticationToken(mockUserDetails, null, null)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Test
    @DisplayName("게스트 삭제 시 분배금이 남은 인원에게 재계산되어야 한다 (3명 -> 2명)")
    fun deleteGuest_recalculation() {
        // Given
        val (trip, owner, member, guest) = createTripWithMembers()

        // 권한 있는 사용자(Owner)로 로그인
        setAuthentication(owner.member!!.id!!)

        // 지출 생성 (총액 30,000원)
        val expense = createExpense(trip, owner, "저녁 식사", BigDecimal("30000"))
        val item = createExpenseItem(expense, "고기", BigDecimal("30000"))

        // 3명이 10,000원씩 분배
        createAssignment(item, owner, BigDecimal("10000"))
        createAssignment(item, member, BigDecimal("10000"))
        createAssignment(item, guest, BigDecimal("10000"))

        // When
        tripMemberService.deleteGuest(trip.id!!, guest.id!!)

        // Then
        // 1. 게스트 삭제 확인
        val deletedGuest = tripMemberRepository.findById(guest.id!!)
        assertThat(deletedGuest).isEmpty

        // 2. 남은 Assignment 확인
        // InitDataService 등으로 생성된 다른 데이터와 섞이지 않도록 현재 아이템(item.id)으로 필터링
        val assignments = expenseAssignmentRepository.findAll().filter { it.expenseItem.id == item.id }

        // 게스트 몫이 사라져서 2개만 남아야 함
        assertThat(assignments).hasSize(2)

        // 3. 금액 재계산 확인 (30,000 / 2명 = 15,000원)
        assignments.forEach { assignment ->
            assertThat(assignment.amount).isEqualByComparingTo(BigDecimal("15000"))
        }
    }

    @Test
    @DisplayName("게스트가 결제한(Payer) 내역은 삭제 시 Owner에게 이관되어야 한다")
    fun deleteGuest_payer_transfer() {
        // Given
        val (trip, owner, _, guest) = createTripWithMembers()

        // 권한 있는 사용자(Owner)로 로그인
        setAuthentication(owner.member!!.id!!)

        // 게스트가 결제한 지출 생성
        val expense = createExpense(trip, guest, "게스트가 쏜 커피", BigDecimal("5000"))

        // When
        tripMemberService.deleteGuest(trip.id!!, guest.id!!)

        // Then
        val updatedExpense = expenseRepository.findById(expense.id!!).get()
        assertThat(updatedExpense.payer.id).isEqualTo(owner.id) // Payer가 Owner로 변경됨
    }

    @Test
    @DisplayName("멤버 강퇴 시 Role이 KICKED로 변경되고 데이터는 유지되어야 한다")
    fun kickMember() {
        // Given
        val (trip, owner, member, _) = createTripWithMembers()

        // 강퇴는 Owner만 가능하므로 Owner로 로그인
        setAuthentication(owner.member!!.id!!)

        // When
        tripMemberService.kickMember(trip.id!!, member.id!!)

        // Then
        val kickedMember = tripMemberRepository.findById(member.id!!).get()
        assertThat(kickedMember.role).isEqualTo(TripRole.KICKED)
        assertThat(kickedMember.member).isNotNull // 연관관계 유지 확인
    }

    @Test
    @DisplayName("여행 탈퇴 시 Role이 EXITED로 변경되어야 한다")
    fun leaveTrip() {
        // Given
        val (trip, _, member, _) = createTripWithMembers()

        // 탈퇴는 본인이 요청해야 하므로 Member로 로그인
        setAuthentication(member.member!!.id!!)

        // When
        tripMemberService.leaveTrip(trip.id!!)

        // Then
        val exitedMember = tripMemberRepository.findById(member.id!!).get()
        assertThat(exitedMember.role).isEqualTo(TripRole.EXITED)
    }

    // --- Helper Methods ---

    private fun createTripWithMembers(): Quadruple<Trip, TripMember, TripMember, TripMember> {
        val trip = tripRepository.save(Trip("Test Trip", LocalDate.now(), LocalDate.now().plusDays(3), Country.JAPAN))

        val memberUser = memberRepository.save(Member("m@test.com", "pw", "member", null, Role.USER, Provider.LOCAL, null, false))
        val ownerUser = memberRepository.save(Member("o@test.com", "pw", "owner", null, Role.USER, Provider.LOCAL, null, false))

        val owner = tripMemberRepository.save(TripMember(ownerUser, trip, null, TripRole.OWNER))
        val member = tripMemberRepository.save(TripMember(memberUser, trip, null, TripRole.MEMBER))
        val guest = tripMemberRepository.save(TripMember(null, trip, "Guest1", TripRole.GUEST))

        return Quadruple(trip, owner, member, guest)
    }

    private fun createExpense(trip: Trip, payer: TripMember, title: String, amount: BigDecimal): Expense {
        val category = categoryRepository.save(Category(trip, 1, "Cat", 1))
        val itinerary = itineraryItemRepository.save(ItineraryItem(category, null, "Item", null, 1, null))

        return expenseRepository.save(
            Expense(trip, itinerary, payer, title, amount, InputMethod.HANDWRITE, null, "KRW")
        )
    }

    private fun createExpenseItem(expense: Expense, name: String, price: BigDecimal): ExpenseItem {
        val item = ExpenseItem(expense, name, price)
        expense.addExpenseItem(item)
        return expenseItemRepository.save(item)
    }

    private fun createAssignment(item: ExpenseItem, member: TripMember, amount: BigDecimal) {
        val assignment = ExpenseAssignment(item, member, amount)
        item.assignments.add(assignment)
        expenseAssignmentRepository.save(assignment)
    }

    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}