package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import com.tribe.tribe_api.itinerary.dto.ItineraryRequest
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@Transactional
class ItineraryServiceIntegrationTest @Autowired constructor(
    private val itineraryService: ItineraryService,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tripRepository: TripRepository,
    private val placeRepository: PlaceRepository,
    private val categoryRepository: CategoryRepository,
    private val itineraryItemRepository: ItineraryItemRepository,
) {
    private lateinit var member: Member
    private lateinit var nonMember: Member
    private lateinit var trip: Trip
    private lateinit var place: Place
    private lateinit var category: Category
    private lateinit var savedItem: ItineraryItem

    @BeforeEach
    fun setUp() {
        // 1. 사용자 생성
        member = memberRepository.save(Member
            ("member@test.com", passwordEncoder.encode("pw"),
            "멤버",
            null, Role.USER, Provider.LOCAL,
            null,
            false))

        nonMember = memberRepository.save(Member
            ("nonmember@test.com",
            passwordEncoder.encode("pw"),
            "비멤버",
            null, Role.USER, Provider.LOCAL,
            null,
            false))

        // 2. 여행 데이터 생성
        trip = Trip("테스트 여행",
            LocalDate.now(), LocalDate.now().plusDays(5),
            Country.JAPAN)

        trip.addMember(member, TripRole.MEMBER)

        tripRepository.save(trip)

        // 3. 장소 및 카테고리 데이터 생성
        place = placeRepository.save(Place
            ("place_id_1",
            "테스트 장소",
            "테스트 주소", BigDecimal.ZERO, BigDecimal.ZERO))

        category = categoryRepository.save(Category
            (trip, 1, "Day 1", 1))

        // 4. 테스트용 일정 데이터 미리 생성
        savedItem = itineraryItemRepository.save(ItineraryItem
            (category, place,
            null, LocalDateTime.now(),
            1,
            "기존 메모"))
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Test
    @DisplayName("일정 생성 성공 - 장소(Place) 기반")
    fun createItinerary_withPlaceId_Success() {
        setAuthentication(member)
        val request = ItineraryRequest.Create(placeId = place.id, title = null,
            time = LocalDateTime.now(), memo = "새로운 메모")

        val response = itineraryService.createItinerary(category.id!!, request)

        assertThat(response.itineraryId).isNotNull()
        assertThat(response.name).isEqualTo(place.name)
        assertThat(response.memo).isEqualTo("새로운 메모")
        assertThat(response.location).isNotNull()
    }

    @Test
    @DisplayName("일정 생성 성공 - 텍스트(Title) 기반")
    fun createItinerary_withTitle_Success() {

        setAuthentication(member)
        val request = ItineraryRequest.Create(placeId = null, title = "직접 입력한 일정",
            time = null, memo = null)


        val response = itineraryService.createItinerary(category.id!!, request)


        assertThat(response.itineraryId).isNotNull()
        assertThat(response.name).isEqualTo("직접 입력한 일정")
        assertThat(response.location).isNull()
    }

    @Test
    @DisplayName("일정 생성 실패 - placeId와 title이 모두 없는 경우")
    fun createItinerary_Fail_When_BothPlaceAndTitleAreNull() {

        setAuthentication(member)
        val request = ItineraryRequest.Create(placeId = null, title = null, time = null, memo = null)


        val exception = assertThrows<BusinessException> { itineraryService.createItinerary(category.id!!, request) }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.INVALID_INPUT_VALUE)
    }

    @Test
    @DisplayName("일정 생성 실패 - 여행 멤버가 아닌 경우")
    fun createItinerary_Fail_When_UserIsNotMember() {

        setAuthentication(nonMember)
        val request = ItineraryRequest.Create(placeId = place.id, title = null, time = null, memo = null)


        val exception = assertThrows<BusinessException> { itineraryService.createItinerary(category.id!!, request) }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
    }

    @Test
    @DisplayName("카테고리별 일정 조회 성공")
    fun getItinerariesByCategory_Success() {

        setAuthentication(member)


        val responses = itineraryService.getItinerariesByCategory(trip.id!!, category.id!!)


        assertThat(responses).hasSize(1)
        assertThat(responses[0].itineraryId).isEqualTo(savedItem.id)
        assertThat(responses[0].name).isEqualTo(place.name)
    }

    @Test
    @DisplayName("일정 수정 성공")
    fun updateItinerary_Success() {

        setAuthentication(member)
        val newTime = LocalDateTime.now().plusHours(1)
        val newMemo = "수정된 메모입니다."
        val request = ItineraryRequest.Update(time = newTime, memo = newMemo)


        val response = itineraryService.updateItinerary(savedItem.id!!, request)


        assertThat(response.itineraryId).isEqualTo(savedItem.id)
        assertThat(response.time).isEqualTo(newTime)
        assertThat(response.memo).isEqualTo(newMemo)
    }

    @Test
    @DisplayName("일정 삭제 성공")
    fun deleteItinerary_Success() {

        setAuthentication(member)
        val itemId = savedItem.id!!


        itineraryService.deleteItinerary(itemId)


        val foundItem = itineraryItemRepository.findById(itemId)
        assertThat(foundItem.isPresent).isFalse()
    }

    @Test
    @DisplayName("일정 순서 변경 성공")
    fun updateItineraryOrder_Success() {

        setAuthentication(member)
        val item2 = itineraryItemRepository.save(ItineraryItem(category, place, "두 번째", null, 2, null))
        val item3 = itineraryItemRepository.save(ItineraryItem(category, place, "세 번째", null, 3, null))


        val request = ItineraryRequest.OrderUpdate(
            items = listOf(
                ItineraryRequest.OrderItem(item3.id!!, 1),
                ItineraryRequest.OrderItem(savedItem.id!!, 2),
                ItineraryRequest.OrderItem(item2.id!!, 3)
            )
        )

        itineraryService.updateItineraryOrder(trip.id!!, request)

        val foundItem1 = itineraryItemRepository.findById(savedItem.id!!).get()
        val foundItem2 = itineraryItemRepository.findById(item2.id!!).get()
        val foundItem3 = itineraryItemRepository.findById(item3.id!!).get()

        assertThat(foundItem1.order).isEqualTo(2)
        assertThat(foundItem2.order).isEqualTo(3)
        assertThat(foundItem3.order).isEqualTo(1)
    }

    @Test
    @DisplayName("카테고리별 일정 조회 실패 - 여행 멤버가 아닌 경우")
    fun getItinerariesByCategory_Fail_When_UserIsNotMember() {

        setAuthentication(nonMember)


        val exception = assertThrows<BusinessException> {
            itineraryService.getItinerariesByCategory(trip.id!!, category.id!!)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
    }

    @Test
    @DisplayName("일정 수정 실패 - 여행 멤버가 아닌 경우")
    fun updateItinerary_Fail_When_UserIsNotMember() {

        setAuthentication(nonMember)
        val request = ItineraryRequest.Update(time = LocalDateTime.now(), memo = "해킹 시도")

        val exception = assertThrows<BusinessException> {
            itineraryService.updateItinerary(savedItem.id!!, request)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.NO_AUTHORITY_TRIP)
    }



}
