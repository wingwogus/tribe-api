package com.tribe.tribe_api

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
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.annotation.PostConstruct
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime // [추가!] LocalDateTime을 사용하기 위해 import 합니다.


@Component
class InitDataService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tripRepository: TripRepository,
    private val placeRepository: PlaceRepository,
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository
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

        val dotonbori = placeRepository.save(Place("dotonbori_id", "도톤보리", "일본 오사카시", BigDecimal("34.6688"), BigDecimal("135.5013")))
        val osakaCastle = placeRepository.save(Place("osaka_castle_id", "오사카성", "일본 오사카시", BigDecimal("34.6873"), BigDecimal("135.5262")))
        val usj = placeRepository.save(Place("usj_id", "유니버설 스튜디오 재팬", "일본 오사카시", BigDecimal("34.6654"), BigDecimal("135.4323")))


        val day1Category = categoryRepository.save(Category(trip, 1, "1일차: 오사카 도착", 1))

        itineraryItemRepository.save(
            ItineraryItem(
                category = day1Category,
                place = usj,
                title = null,
                time = trip.startDate.atTime(14, 0),
                order = 1,
                memo = "익스프레스 티켓 구매하기"
            )
        )

        itineraryItemRepository.save(
            ItineraryItem(
                category = day1Category,
                place = dotonbori,
                title = null,
                time = trip.startDate.atTime(19, 30),
                order = 2,
                memo = "글리코상 앞에서 사진찍기"
            )
        )

        val day2Category = categoryRepository.save(Category(trip, 2, "2일차: 성곽 투어", 1))

        itineraryItemRepository.save(
            ItineraryItem(
                category = day2Category,
                place = osakaCastle,
                title = null,
                time = trip.startDate.plusDays(1).atTime(10, 0),
                order = 1,
                memo = "천수각 입장"
            )
        )
        // 5-2. 텍스트 기반 일정 (title 사용)
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
    }
}
