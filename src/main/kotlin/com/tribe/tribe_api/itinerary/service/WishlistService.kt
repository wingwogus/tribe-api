package com.tribe.tribe_api.itinerary.service


import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.itinerary.dto.WishlistDto
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.entity.WishlistItem
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.itinerary.repository.WishlistItemRepository
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WishlistService(
    private val wishlistItemRepository: WishlistItemRepository,
    private val placeRepository: PlaceRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripRepository: TripRepository,
    private val memberRepository: MemberRepository
) {

    // 위시리스트에 장소 추가
    fun addWishList(
        tripId : Long,
        placeDto : WishlistDto.WishListAddRequest
        ) : WishlistDto.WishlistItemDto {

        val memberId = SecurityUtil.getCurrentMemberId()
        val member = memberRepository.findByIdOrNull(memberId) ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        val trip = tripRepository.findByIdOrNull(tripId) ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)
        val tripMember = tripMemberRepository.findByTripAndMember(trip, member) ?: throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER)

        val placeEntity = placeRepository.findByExternalPlaceId(placeDto.placeId)
            ?: run {
                val newPlace = Place(
                    externalPlaceId = placeDto.placeId,
                    name = placeDto.placeName,
                    address = placeDto.address,
                    latitude = placeDto.latitude,
                    longitude = placeDto.longitude
                )
                placeRepository.save(newPlace)
            }

        val wishlistItem = WishlistItem(
            trip = trip,
            place = placeEntity,
            adder = tripMember
        )
        val savedWishlistItem = wishlistItemRepository.save(wishlistItem)
        trip.wishlistItems.add(savedWishlistItem)
        tripMember.wishlistItems.add(savedWishlistItem)

        return WishlistDto.WishlistItemDto.from(savedWishlistItem)
    }

    // 위시리스트 내에서 장소 검색
    @Transactional(readOnly = true)
    fun searchWishList(tripId: Long, query: String, pageable: Pageable): WishlistDto.WishlistSearchResponse {
        val wishlistPage = wishlistItemRepository.findAllByTrip_IdAndPlace_NameContainingIgnoreCase(tripId, query, pageable)
        return WishlistDto.WishlistSearchResponse.from(wishlistPage)
    }

    // 위시리스트 목록 조회 (페이지 네이션)
    @Transactional(readOnly = true)
    fun getWishList(tripId: Long, pageable: Pageable): WishlistDto.WishlistSearchResponse {
        val wishlistPage = wishlistItemRepository.findAllByTrip_Id(tripId, pageable)
        return WishlistDto.WishlistSearchResponse.from(wishlistPage)
    }

    // 위시리스트 장소 제거
    fun deleteWishlistItems(wishlistItemIds: List<Long>) {

        val requestedIds = wishlistItemIds.distinct()
        if (requestedIds.isEmpty()) return

        val existingIds = wishlistItemRepository.findExistingIds(requestedIds)

        val missingIds = requestedIds.filterNot { it in existingIds }

        if (missingIds.isNotEmpty()) {
            throw BusinessException(ErrorCode.WISHLIST_ITEM_NOT_FOUND)
        }
        wishlistItemRepository.deleteAllByIdInBatch(requestedIds)
    }
}