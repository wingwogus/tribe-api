package com.tribe.tribe_api.itinerary.service


import com.tribe.tribe_api.itinerary.dto.WishlistDto
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.entity.WishlistItem
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.itinerary.repository.WishlistItemRepository
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WishlistService(
    private val wishlistItemRepository: WishlistItemRepository,
    private val placeRepository: PlaceRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripRepository: TripRepository
) {

    // 위시리스트에 장소 추가
    fun addWishList(
        member : Member,
        tripId : Long,
        placeDto : WishlistDto.WishListAddRequest
        ) : WishlistDto.WishlistItemDto {

        val trip = tripRepository.findById(tripId).orElseThrow()
        val tripMember = tripMemberRepository.findByTripAndMember(trip, member).orElseThrow()

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
    fun deleteWishlistItem(wishlistItemId: Long) {
        if (!wishlistItemRepository.existsById(wishlistItemId)) {
            throw EntityNotFoundException("해당 위시리스트 항목을 찾을 수 없습니다. id: $wishlistItemId")
        }
        wishlistItemRepository.deleteById(wishlistItemId)
    }

    fun deleteWishlistItems(wishlistItemIds: List<Long>) {
        wishlistItemRepository.deleteAllByIdInBatch(wishlistItemIds)
    }
}