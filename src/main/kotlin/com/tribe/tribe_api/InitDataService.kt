package com.tribe.tribe_api

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
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.annotation.PostConstruct
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate


@Component
class InitDataService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tripRepository: TripRepository,
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val expenseRepository: ExpenseRepository,
    private val expenseItemRepository: ExpenseItemRepository,
    private val expenseAssignmentRepository: ExpenseAssignmentRepository,
) {
    @PostConstruct
    fun dbInit() {
        if (memberRepository.findByEmail("user1@example.com") != null) {
            return
        }

        val memberA = Member(
            email = "user1@example.com",
            password = passwordEncoder.encode("password"),
            nickname = "테스터A",
            provider = Provider.LOCAL,
            role = Role.USER,
            isFirstLogin = false
        ).apply { memberRepository.save(this) }

        val memberB = Member(
            email = "user2@example.com",
            password = passwordEncoder.encode("password"),
            nickname = "테스터B",
            provider = Provider.LOCAL,
            role = Role.USER,
            isFirstLogin = false
        ).apply { memberRepository.save(this) }

        val trip =
            Trip(
                "일본 오사카 미식 여행",
                LocalDate.of(2025, 10, 26),
                LocalDate.of(2025, 10, 30),
                Country.JAPAN
            )

        trip.addMember(memberA, TripRole.OWNER)
        trip.addMember(memberB, TripRole.MEMBER)

        tripRepository.save(trip)

        val tripMemberA = tripMemberRepository.findByTripIdAndRole(trip.id!!, TripRole.OWNER).first()
        val tripMemberB = tripMemberRepository.findByTripIdAndRole(trip.id!!, TripRole.MEMBER).first()


        val day1Category = categoryRepository.save(Category(trip, 1, "1일차: 오사카 도착", 1))

        itineraryItemRepository.save(
            ItineraryItem(
                category = day1Category,
                place = null,
                title = "오사카 성",
                time = trip.startDate.atTime(14, 0),
                order = 1,
                memo = "익스프레스 티켓 구매하기"
            )
        )

        val dinnerItinerary = itineraryItemRepository.save(
            ItineraryItem(
                category = day1Category,
                place = null,
                title = "도톤보리",
                time = trip.startDate.atTime(19, 30),
                order = 2,
                memo = "글리코상 앞에서 사진찍기"
            )
        )

        val day2Category = categoryRepository.save(Category(trip, 2, "2일차: 성곽 투어", 1))

        itineraryItemRepository.save(
            ItineraryItem(
                category = day2Category,
                place = null,
                title = "천수각",
                time = trip.startDate.plusDays(1).atTime(10, 0),
                order = 1,
                memo = "천수각 입장"
            )
        )

        val day2Cat1 = categoryRepository.save(
            Category(
                trip,
                2,
                "박물관",
                1
            )
        )
        itineraryItemRepository.save(
            ItineraryItem(
                category = day2Category,
                place = null,
                title = "호텔에서 휴식 및 짐 정리",
                time = trip.startDate.plusDays(1).atTime(17, 0),
                order = 2,
                memo = "저녁 식사 장소 찾아보기"
            )
        )

        val dinnerExpense = Expense(
            trip = trip,
            itineraryItem = dinnerItinerary,
            payer = tripMemberA,
            title = "도톤보리 저녁 식사 (타코야끼, 오꼬노미야끼)",
            totalAmount = BigDecimal("10000"),
            entryMethod = InputMethod.HANDWRITE,
            currency = "JPY"
        ).apply { expenseRepository.save(this) }

        val foodItem = ExpenseItem(
            expense = dinnerExpense,
            name = "식사 및 주류",
            price = BigDecimal("10000")
        ).apply { expenseItemRepository.save(this) }

        dinnerExpense.addExpenseItem(foodItem)
        expenseRepository.save(dinnerExpense)

        val assignmentAmount = BigDecimal("5000")

        expenseAssignmentRepository.save(
            ExpenseAssignment(
                expenseItem = foodItem,
                tripMember = tripMemberA,
                amount = assignmentAmount
            )
        )

        expenseAssignmentRepository.save(
            ExpenseAssignment(
                expenseItem = foodItem,
                tripMember = tripMemberB,
                amount = assignmentAmount
            )
        )
    }
}