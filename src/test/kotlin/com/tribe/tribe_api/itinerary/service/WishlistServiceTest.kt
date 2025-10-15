package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.itinerary.dto.WishlistDto
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.entity.WishlistItem
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.itinerary.repository.WishlistItemRepository
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
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@ExtendWith(MockKExtension::class)
class WishlistServiceTest {

    @MockK private lateinit var wishlistItemRepository: WishlistItemRepository
    @MockK private lateinit var placeRepository: PlaceRepository
    @MockK private lateinit var tripMemberRepository: TripMemberRepository
    @MockK private lateinit var tripRepository: TripRepository
    @MockK private lateinit var memberRepository: MemberRepository

    @InjectMockKs
    private lateinit var wishlistService: WishlistService

    // ✨ 테스트 전역에서 사용할 실제 엔티티 객체들
    private lateinit var member: Member
    private lateinit var trip: Trip
    private lateinit var tripMember: TripMember
    private val memberId = 1L
    private val tripId = 10L

    @BeforeEach
    fun setUp() {
        // 실제 Member, Trip, TripMember 객체 생성
        member = Member("user1@test.com", "pw1", "user1", role = Role.USER, provider = Provider.LOCAL, isFirstLogin = false)
        trip = Trip("일본 여행", LocalDate.now(), LocalDate.now().plusDays(5), Country.JAPAN)
        tripMember = TripMember(member, trip, null,TripRole.OWNER)

        // SecurityUtil Mocking
        mockkObject(SecurityUtil)
        every { SecurityUtil.getCurrentMemberId() } returns memberId
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(SecurityUtil)
    }

    @Test
    @DisplayName("위시리스트 추가 성공 - 새로운 장소")
    fun `새로운 장소를 위시리스트에 추가하면 Place와 WishlistItem이 모두 저장된다`() {
        // given
        val placeDto = WishlistDto.WishListAddRequest("google_place_1", "도쿄타워", "주소", BigDecimal.ZERO, BigDecimal.ZERO)

        // Repository들의 동작을 setUp에서 만든 실제 객체들로 정의
        every { memberRepository.findByIdOrNull(memberId) } returns member
        every { tripRepository.findByIdOrNull(tripId) } returns trip
        every { tripMemberRepository.findByTripAndMember(trip, member) } returns tripMember
        every { placeRepository.findByExternalPlaceId(placeDto.placeId) } returns null
        every { placeRepository.save(any()) } answers { firstArg() }
        every { wishlistItemRepository.save(any()) } answers { firstArg() }

        // when
        val result = wishlistService.addWishList(tripId, placeDto)

        // then
        assertThat(result.name).isEqualTo(placeDto.placeName)
        verify(exactly = 1) { placeRepository.save(any()) }
        verify(exactly = 1) { wishlistItemRepository.save(any()) }
    }

    @Test
    @DisplayName("위시리스트 추가 성공 - 기존 장소")
    fun `이미 DB에 있는 장소를 위시리스트에 추가하면 Place는 저장되지 않는다`() {
        // given
        val placeDto = WishlistDto.WishListAddRequest("google_place_2", "오사카성", "주소", BigDecimal.ZERO, BigDecimal.ZERO)
        val existingPlace = Place("google_place_2", "오사카성", "주소", BigDecimal.ZERO, BigDecimal.ZERO)

        every { memberRepository.findByIdOrNull(memberId) } returns member
        every { tripRepository.findByIdOrNull(tripId) } returns trip
        every { tripMemberRepository.findByTripAndMember(trip, member) } returns tripMember
        every { placeRepository.findByExternalPlaceId(placeDto.placeId) } returns existingPlace
        every { wishlistItemRepository.save(any()) } answers { firstArg() }

        // when
        val result = wishlistService.addWishList(tripId, placeDto)

        // then
        assertThat(result.name).isEqualTo(existingPlace.name)
        verify(exactly = 0) { placeRepository.save(any()) }
        verify(exactly = 1) { wishlistItemRepository.save(any()) }
    }

    @Test
    @DisplayName("위시리스트 전체 조회 성공")
    fun `getWishList는 페이지네이션된 전체 위시리스트 DTO를 반환한다`() {
        // given
        val pageable = PageRequest.of(0, 10)
        val place = Place("id", "name", "addr", BigDecimal.ONE, BigDecimal.ONE)
        val fakeItems = listOf(WishlistItem(trip, place, tripMember))
        val fakePage = PageImpl(fakeItems, pageable, fakeItems.size.toLong())

        every { wishlistItemRepository.findAllByTrip_Id(tripId, pageable) } returns fakePage

        // when
        val result = wishlistService.getWishList(tripId, pageable)

        // then
        assertThat(result.content).hasSize(1)
        assertThat(result.pageNumber).isEqualTo(0)
    }

    @Test
    @DisplayName("위시리스트 검색 성공")
    fun `searchWishList는 검색 조건에 맞는 페이지네이션된 DTO를 반환한다`() {
        // given
        val query = "도쿄"
        val pageable = PageRequest.of(0, 10)
        val place = Place("id", "도쿄타워", "addr", BigDecimal.ONE, BigDecimal.ONE)
        val fakeItems = listOf(WishlistItem(trip, place, tripMember))
        val fakePage = PageImpl(fakeItems, pageable, fakeItems.size.toLong())

        every { wishlistItemRepository.findAllByTrip_IdAndPlace_NameContainingIgnoreCase(tripId, query, pageable) } returns fakePage

        // when
        val result = wishlistService.searchWishList(tripId, query, pageable)

        // then
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].name).isEqualTo("도쿄타워")
    }

    @Test
    @DisplayName("위시리스트 다중 삭제 성공")
    fun `deleteWishlistItems는 모든 ID가 존재할 때 정상적으로 삭제를 호출한다`() {
        // given
        val idsToDelete = listOf(1L, 2L)
        every { wishlistItemRepository.findExistingIds(idsToDelete) } returns idsToDelete
        every { wishlistItemRepository.deleteAllByIdInBatch(idsToDelete) } just runs

        // when
        wishlistService.deleteWishlistItems(idsToDelete)

        // then
        verify(exactly = 1) { wishlistItemRepository.deleteAllByIdInBatch(idsToDelete) }
    }

    @Test
    @DisplayName("위시리스트 다중 삭제 실패 - 존재하지 않는 ID 포함")
    fun `deleteWishlistItems는 존재하지 않는 ID가 포함되면 예외를 던진다`() {
        // given
        val idsToDelete = listOf(1L, 999L)
        every { wishlistItemRepository.findExistingIds(idsToDelete) } returns listOf(1L)

        // when & then
        assertThrows<BusinessException> {
            wishlistService.deleteWishlistItems(idsToDelete)
        }
        verify(exactly = 0) { wishlistItemRepository.deleteAllByIdInBatch(any()) }
    }
}