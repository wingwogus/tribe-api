package com.tribe.tribe_api

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
    private val placeRepository: PlaceRepository,
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val expenseRepository: ExpenseRepository
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
            Trip("일본 오사카 미식 여행",
                LocalDate.of(2025, 10, 26),
                LocalDate.of(2025, 10, 30),
                Country.JAPAN
            )

        trip.addMember(memberA, TripRole.OWNER)
        trip.addMember(memberB, TripRole.MEMBER)

        tripRepository.save(trip)

        val dotonbori = placeRepository.save(
            Place(
                "dotonbori_id",
                "도톤보리",
                "일본 오사카시",
                BigDecimal("34.6688"),
                BigDecimal("135.5013")
            )
        )
        val osakaCastle = placeRepository.save(
            Place(
                "osaka_castle_id",
                "오사카성",
                "일본 오사카시",
                BigDecimal("34.6873"),
                BigDecimal("135.5262")
            )
        )
        val usj = placeRepository.save(
            Place(
                "usj_id",
                "효고 박물관",
                "일본 오사카시",
                BigDecimal("34.582007"),
                BigDecimal("135.925498")
            )
        )

        val day1Cat1 = categoryRepository.save(
            Category(
                trip,
                1,
                "오후 관광",
                1
            )
        )

        itineraryItemRepository.save(
            ItineraryItem(
                day1Cat1,
                usj,
                1,
                "천수각 입장권 예매하기"
            )
        )

        val day1Cat2 = categoryRepository.save(
            Category(
                trip,
                1,
                "저녁 식사",
                2
            )
        )
        val dinnerItinerary = itineraryItemRepository.save(
            ItineraryItem(
                day1Cat2,
                dotonbori,
                1,
                "글리코상 앞에서 사진찍기"
            )
        )

        val day2Cat1 = categoryRepository.save(
            Category(trip,
                2,
                "박물관",
                1
            )
        )
        itineraryItemRepository.save(
            ItineraryItem(
                day2Cat1,
                osakaCastle,
                1,
                null
            )
        )

        // 정산 테스트 더미
        val guestSihwan = TripMember(member = null, trip = trip, guestNickname = "시환", role = TripRole.GUEST)
            .apply { tripMemberRepository.save(this) }
        trip.members.add(guestSihwan)

        val expenseForDinner = Expense(
            trip = trip,
            itineraryItem = dinnerItinerary,
            payer = trip.members.first { it.member?.nickname == "테스터A" },
            title = "저녁 식사비",
            totalAmount = BigDecimal(30000),
            entryMethod = InputMethod.HANDWRITE,
            paymentDate = LocalDate.of(2025, 10, 26)
        )

        val dinnerItem1 = ExpenseItem(expenseForDinner, "라면과 맥주", BigDecimal(25000))
        val dinnerItem2 = ExpenseItem(expenseForDinner, "음료수", BigDecimal(5000))
        expenseForDinner.expenseItems.addAll(listOf(dinnerItem1, dinnerItem2))

        dinnerItem1.assignments.add(ExpenseAssignment(dinnerItem1, trip.members.first { it.member?.nickname == "테스터A" }))
        dinnerItem1.assignments.add(ExpenseAssignment(dinnerItem1, trip.members.first { it.member?.nickname == "테스터B" }))
        dinnerItem2.assignments.add(ExpenseAssignment(dinnerItem2, guestSihwan))

        expenseRepository.save(expenseForDinner)

        // 여러명 결제 같은 날에 테스터 B가 편의점 간식을 사고 정산하는 더미
        val expenseForSnack = Expense(
            trip = trip,
            itineraryItem = dinnerItinerary,
            payer = trip.members.first { it.member?.nickname == "테스터B" },
            title = "편의점 간식",
            totalAmount = BigDecimal(10000),
            entryMethod = InputMethod.HANDWRITE,
            paymentDate = LocalDate.of(2025, 10, 26)
        )

        val snackItem1 = ExpenseItem(expenseForSnack, "테스터A 간식", BigDecimal(3333))
        val snackItem2 = ExpenseItem(expenseForSnack, "테스터B 간식", BigDecimal(3334))
        val snackItem3 = ExpenseItem(expenseForSnack, "시환 간식", BigDecimal(3333))
        expenseForSnack.expenseItems.addAll(listOf(snackItem1, snackItem2, snackItem3))

        snackItem1.assignments.add(ExpenseAssignment(snackItem1, trip.members.first { it.member?.nickname == "테스터A" }))
        snackItem2.assignments.add(ExpenseAssignment(snackItem2, trip.members.first { it.member?.nickname == "테스터B" }))
        snackItem3.assignments.add(ExpenseAssignment(snackItem3, guestSihwan))

        expenseRepository.save(expenseForSnack)
    }
}