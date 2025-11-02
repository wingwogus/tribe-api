package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetails
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@Transactional
class WishlistServiceTest @Autowired constructor(
    private val wishlistService: WishlistService,
    private val wishlistItemRepository: WishlistItemRepository,
    private val placeRepository: PlaceRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripRepository: TripRepository,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder
) {
    private lateinit var member: Member
    private lateinit var nonMember: Member
    private lateinit var trip: Trip
    private lateinit var tripMember: TripMember
    private lateinit var existingPlace: Place
    private lateinit var existingWishlistItem: WishlistItem

    @BeforeEach
    fun setUp() {
        wishlistItemRepository.deleteAll()
        tripMemberRepository.deleteAll()
        tripRepository.deleteAll()
        memberRepository.deleteAll()
        placeRepository.deleteAll()

        member = memberRepository.save(
            Member(
                "member@wishlist.com", passwordEncoder.encode("pw"), "위시리스트멤버",
                null, Role.USER, Provider.LOCAL, null, false
            )
        )
        nonMember = memberRepository.save(
            Member(
                "nonmember@wishlist.com", passwordEncoder.encode("pw"), "비멤버",
                null, Role.USER, Provider.LOCAL, null, false
            )
        )
        trip = tripRepository.save(
            Trip("위시리스트 여행", LocalDate.now(), LocalDate.now().plusDays(3), Country.JAPAN)
        )
        tripMember = tripMemberRepository.save(
            TripMember(member, trip, null, TripRole.OWNER)
        )
        existingPlace = placeRepository.save(
            Place("existing_place_1", "오사카성", "오사카 주소", BigDecimal.TEN, BigDecimal.TEN)
        )
        existingWishlistItem = wishlistItemRepository.save(
            WishlistItem(trip, existingPlace, tripMember)
        )

        SecurityContextHolder.clearContext()
    }

    private fun setAuthentication(member: Member) {
        val userDetails = CustomUserDetails(member)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Nested
    @DisplayName("위시리스트 추가 (addWishList)")
    inner class AddWishListTest {
        @Test
        @DisplayName("성공 - 새로운 장소")
        fun addWishList_withNewPlace_Success() {
            // given
            setAuthentication(member)
            val request = WishlistDto.WishListAddRequest(
                "new_google_place_1", "도쿄타워", "도쿄 주소",
                BigDecimal.ZERO, BigDecimal.ZERO
            )
            val initialPlaceCount = placeRepository.count()
            val initialWishlistCount = wishlistItemRepository.count()

            // when
            val result = wishlistService.addWishList(trip.id!!, request)

            // then
            assertThat(result.name).isEqualTo("도쿄타워")
            assertThat(placeRepository.count()).isEqualTo(initialPlaceCount + 1)
            assertThat(wishlistItemRepository.count()).isEqualTo(initialWishlistCount + 1)
            assertThat(placeRepository.findByExternalPlaceId("new_google_place_1")).isNotNull

            // 'member'('위시리스트멤버')가 추가했는지 확인
            assertThat(result.adderTripMemberId).isEqualTo(tripMember.id!!)
            assertThat(result.adderName).isEqualTo(member.nickname)
        }

        @Test
        @DisplayName("성공 - Place는 이미 존재하지만 위시리스트에는 없음")
        fun addWishList_withExistingPlace_Success() {
            // given
            setAuthentication(member)
            wishlistItemRepository.delete(existingWishlistItem)

            val request = WishlistDto.WishListAddRequest(
                existingPlace.externalPlaceId,
                existingPlace.name,
                existingPlace.address,
                existingPlace.latitude,
                existingPlace.longitude
            )
            val initialPlaceCount = placeRepository.count()
            val initialWishlistCount = wishlistItemRepository.count()

            // when
            val result = wishlistService.addWishList(trip.id!!, request)

            // then
            assertThat(result.name).isEqualTo("오사카성")
            assertThat(placeRepository.count()).isEqualTo(initialPlaceCount)
            assertThat(wishlistItemRepository.count()).isEqualTo(initialWishlistCount + 1)

            assertThat(result.adderTripMemberId).isEqualTo(tripMember.id!!)
            assertThat(result.adderName).isEqualTo(member.nickname)
        }


        @Test
        @DisplayName("실패 - 여행 멤버가 아닌 경우")
        fun addWishList_Fail_WhenNotTripMember() {
            setAuthentication(nonMember)
            val request = WishlistDto.WishListAddRequest("new_google_place_1", "도쿄타워", "도쿄 주소", BigDecimal.ZERO, BigDecimal.ZERO)

            val exception = assertThrows<BusinessException> {
                wishlistService.addWishList(trip.id!!, request)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 여행 ID")
        fun addWishList_Fail_WhenTripNotFound() {
            setAuthentication(member)
            val request = WishlistDto.WishListAddRequest("new_google_place_1", "도쿄타워", "도쿄 주소", BigDecimal.ZERO, BigDecimal.ZERO)
            val nonExistingTripId = 999L

            val exception = assertThrows<BusinessException> {
                wishlistService.addWishList(nonExistingTripId, request)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.TRIP_NOT_FOUND)
        }

        @Test
        @DisplayName("실패 - 이미 위시리스트에 존재하는 장소 (중복)")
        fun addWishList_Fail_WhenItemAlreadyExists() {
            setAuthentication(member)
            val request = WishlistDto.WishListAddRequest(
                existingPlace.externalPlaceId,
                existingPlace.name,
                existingPlace.address,
                existingPlace.latitude,
                existingPlace.longitude
            )

            val exception = assertThrows<BusinessException> {
                wishlistService.addWishList(trip.id!!, request)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.WISHLIST_ITEM_ALREADY_EXISTS)
        }
    }

    @Nested
    @DisplayName("위시리스트 조회 및 검색 (getWishList / searchWishList)")
    inner class GetAndSearchWishListTest {
        @Test
        @DisplayName("위시리스트 전체 조회 성공")
        fun getWishList_Success() {
            // given
            setAuthentication(member)
            val pageable = PageRequest.of(0, 10)

            // when
            val result = wishlistService.getWishList(trip.id!!, pageable)

            // then
            assertThat(result.content).hasSize(1)
            assertThat(result.totalElements).isEqualTo(1)
            assertThat(result.content[0].name).isEqualTo("오사카성")


            assertThat(result.content[0].adderTripMemberId).isEqualTo(tripMember.id!!)
            assertThat(result.content[0].adderName).isEqualTo(member.nickname)
        }

        @Test
        @DisplayName("위시리스트 검색 성공")
        fun searchWishList_Success() {
            // given
            setAuthentication(member)
            val pageable = PageRequest.of(0, 10)

            // when
            val result = wishlistService.searchWishList(trip.id!!, "오사카", pageable)

            // then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name).isEqualTo("오사카성")

            assertThat(result.content[0].adderTripMemberId).isEqualTo(tripMember.id!!)
            assertThat(result.content[0].adderName).isEqualTo(member.nickname)
        }

        @Test
        @DisplayName("위시리스트 검색 성공 - 결과 없음")
        fun searchWishList_Success_NoResult() {
            setAuthentication(member)
            val pageable = PageRequest.of(0, 10)

            val result = wishlistService.searchWishList(trip.id!!, "도쿄", pageable)

            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(0)
        }

        @Test
        @DisplayName("위시리스트 전체 조회 실패 - 여행 멤버가 아닌 경우")
        fun getWishList_Fail_WhenNotTripMember() {
            setAuthentication(nonMember)

            val exception = assertThrows<BusinessException> {
                wishlistService.getWishList(trip.id!!, PageRequest.of(0, 10))
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
        }

        @Test
        @DisplayName("위시리스트 검색 실패 - 여행 멤버가 아닌 경우")
        fun searchWishList_Fail_WhenNotTripMember() {
            setAuthentication(nonMember)

            val exception = assertThrows<BusinessException> {
                wishlistService.searchWishList(trip.id!!, "오사카", PageRequest.of(0, 10))
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
        }

        @Test
        @DisplayName("위시리스트 검색 실패 - 존재하지 않는 여행 ID")
        fun searchWishList_Fail_WhenTripNotFound() {
            setAuthentication(member)
            val nonExistingTripId = 999L

            val exception = assertThrows<BusinessException> {
                wishlistService.searchWishList(nonExistingTripId, "오사카", PageRequest.of(0, 10))
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.TRIP_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("위시리스트 다중 삭제 (deleteWishlistItems)")
    inner class DeleteWishlistTest {

        @Test
        @DisplayName("다중 삭제 성공")
        fun deleteWishlistItems_Success() {
            setAuthentication(member)
            val tokyoPlace = placeRepository.save(Place("place_2", "도쿄타워", "도쿄 주소", BigDecimal.ONE, BigDecimal.ONE))
            val item2 = wishlistItemRepository.save(WishlistItem(trip, tokyoPlace, tripMember))
            val idsToDelete = listOf(existingWishlistItem.id!!, item2.id!!)

            assertThat(wishlistItemRepository.count()).isEqualTo(2)

            wishlistService.deleteWishlistItems(trip.id!!, idsToDelete)

            assertThat(wishlistItemRepository.count()).isEqualTo(0)
        }

        @Test
        @DisplayName("다중 삭제 성공 - 빈 리스트 요청")
        fun deleteWishlistItems_Success_WithEmptyList() {
            setAuthentication(member)
            val idsToDelete = emptyList<Long>()
            val initialCount = wishlistItemRepository.count()

            wishlistService.deleteWishlistItems(trip.id!!, idsToDelete)

            assertThat(wishlistItemRepository.count()).isEqualTo(initialCount)
        }


        @Test
        @DisplayName("다중 삭제 실패 - 여행 멤버가 아닌 경우")
        fun deleteWishlistItems_Fail_WhenNotTripMember() {
            setAuthentication(nonMember)
            val idsToDelete = listOf(existingWishlistItem.id!!)

            val exception = assertThrows<BusinessException> {
                wishlistService.deleteWishlistItems(trip.id!!, idsToDelete)
            }
            assertThat(exception.errorCode).isEqualTo(ErrorCode.NOT_A_TRIP_MEMBER)
        }

        @Test
        @DisplayName("다중 삭제 실패 - 존재하지 않는 ID 포함")
        fun deleteWishlistItems_Fail_WhenIdNotFound() {
            setAuthentication(member)
            val nonExistingId = 999L
            val idsToDelete = listOf(existingWishlistItem.id!!, nonExistingId)
            val initialCount = wishlistItemRepository.count()

            val exception = assertThrows<BusinessException> {
                wishlistService.deleteWishlistItems(trip.id!!, idsToDelete)
            }

            assertThat(exception.errorCode).isEqualTo(ErrorCode.WISHLIST_ITEM_NOT_FOUND)
            assertThat(wishlistItemRepository.count()).isEqualTo(initialCount)
        }

        @Test
        @DisplayName("다중 삭제 실패 - 다른 여행의 ID 포함")
        fun deleteWishlistItems_Fail_WhenIdIsFromAnotherTrip() {
            setAuthentication(member)

            val anotherTrip = tripRepository.save(Trip("다른 여행", LocalDate.now(), LocalDate.now(), Country.JAPAN))
            val anotherTripMember = tripMemberRepository.save(TripMember(member, anotherTrip, null, TripRole.OWNER))
            val itemFromAnotherTrip = wishlistItemRepository.save(WishlistItem(anotherTrip, existingPlace, anotherTripMember))

            val idsToDelete = listOf(existingWishlistItem.id!!, itemFromAnotherTrip.id!!)

            val initialCount = wishlistItemRepository.count()

            val exception = assertThrows<BusinessException> {
                wishlistService.deleteWishlistItems(trip.id!!, idsToDelete)
            }

            assertThat(exception.errorCode).isEqualTo(ErrorCode.WISHLIST_ITEM_NOT_FOUND)

            assertThat(wishlistItemRepository.count()).isEqualTo(initialCount)
            assertThat(wishlistItemRepository.existsById(existingWishlistItem.id!!)).isTrue()
            assertThat(wishlistItemRepository.existsById(itemFromAnotherTrip.id!!)).isTrue()
        }
    }
}