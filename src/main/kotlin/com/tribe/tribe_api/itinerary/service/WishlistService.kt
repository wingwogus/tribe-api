package com.tribe.tribe_api.itinerary.service


import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.socket.SocketDto
import com.tribe.tribe_api.itinerary.dto.WishlistDto
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.entity.WishlistItem
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.itinerary.repository.WishlistItemRepository
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WishlistService(
    private val wishlistItemRepository: WishlistItemRepository,
    private val placeRepository: PlaceRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val tripRepository: TripRepository,
    private val memberRepository: MemberRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    // 위시리스트에 장소 추가
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun addWishList(
        tripId : Long,
        request : WishlistDto.WishListAddRequest
        ) : WishlistDto.WishlistItemDto {

        val memberId = SecurityUtil.getCurrentMemberId()
        val member = memberRepository.findByIdOrNull(memberId) ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        val trip = tripRepository.findByIdOrNull(tripId) ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)
        val tripMember = tripMemberRepository.findByTripAndMember(trip, member)!!

        val alreadyExists = wishlistItemRepository.existsByTrip_IdAndPlace_ExternalPlaceId(
            tripId,
            request.externalPlaceId
        )

        if (alreadyExists) {
            throw BusinessException(ErrorCode.WISHLIST_ITEM_ALREADY_EXISTS)
        }

        val place = placeRepository.findByExternalPlaceId(request.externalPlaceId)
            ?: run {
                val newPlace = Place(
                    externalPlaceId = request.externalPlaceId,
                    name = request.placeName,
                    address = request.address,
                    latitude = request.latitude,
                    longitude = request.longitude
                )
                placeRepository.save(newPlace)
            }

        val wishlistItem = WishlistItem(
            trip = trip,
            place = place,
            adder = tripMember
        )
        val savedWishlistItem = wishlistItemRepository.save(wishlistItem)
        trip.wishlistItems.add(savedWishlistItem)
        tripMember.wishlistItems.add(savedWishlistItem)

        val savedItem = WishlistDto.WishlistItemDto.from(savedWishlistItem)

        logger.info("Wishlist item added. Item ID: {}, Trip ID: {}, Place Name: {}", savedWishlistItem.id, tripId, request.placeName)

        val socketMessage = SocketDto.TripEditMessage(
            SocketDto.EditType.ADD_WISHLIST,
            tripId,
            memberId,
            savedItem
        )

        messagingTemplate.convertAndSend("/topic/trips/$tripId", socketMessage)

        return savedItem
    }

    // 위시리스트 내에서 장소 검색
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    @Transactional(readOnly = true)
    fun searchWishList(tripId: Long, query: String, pageable: Pageable): WishlistDto.WishlistSearchResponse {
        val memberId = SecurityUtil.getCurrentMemberId()
        // 멤버 검증
        memberRepository.findByIdOrNull(memberId)
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        // 여행 검증
        tripRepository.findByIdOrNull(tripId)
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        val wishlistPage = wishlistItemRepository.findAllByTrip_IdAndPlace_NameContainingIgnoreCase(tripId, query, pageable)
        return WishlistDto.WishlistSearchResponse.from(wishlistPage)
    }

    // 위시리스트 목록 조회
    @Transactional(readOnly = true)
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun getWishList(tripId: Long, pageable: Pageable): WishlistDto.WishlistSearchResponse {
        val memberId = SecurityUtil.getCurrentMemberId()

        // 멤버 검증
        memberRepository.findByIdOrNull(memberId)
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        // 여행 검증
        tripRepository.findByIdOrNull(tripId)
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        // 위시리스트 조회
        val wishlistPage = wishlistItemRepository.findAllByTrip_Id(tripId, pageable)
        return WishlistDto.WishlistSearchResponse.from(wishlistPage)
    }

    // 위시리스트 장소 제거
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun deleteWishlistItems(tripId: Long,wishlistItemIds: List<Long>) {

        val memberId = SecurityUtil.getCurrentMemberId()

        // 멤버 검증
        memberRepository.findByIdOrNull(memberId)
            ?: throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        // 여행 검증
        tripRepository.findByIdOrNull(tripId)
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        // 중복 아이디 검증
        val requestedIds = wishlistItemIds.distinct()
        if (requestedIds.isEmpty()) return

        // 존재하지 않는 아이디 검증
        val existingIdsInTrip = wishlistItemRepository.findIdsByTripIdAndIdIn(tripId, requestedIds)

        // 위시리스트 아이템 검증
        val missingIds = requestedIds.filterNot { it in existingIdsInTrip }
        if (missingIds.isNotEmpty()) {
            throw BusinessException(ErrorCode.WISHLIST_ITEM_NOT_FOUND)
        }

        wishlistItemRepository.deleteAllByIdInBatch(existingIdsInTrip)

        val socketMessage = SocketDto.TripEditMessage(
            SocketDto.EditType.DELETE_WISHLIST,
            tripId,
            memberId,
            existingIdsInTrip
        )

        messagingTemplate.convertAndSend("/topic/trips/$tripId", socketMessage)

        logger.info("Wishlist items deleted. Trip ID: {}, Deleted Item IDs: {}", tripId, existingIdsInTrip)
    }
}