package com.tribe.tribe_api.itinerary.service

import org.slf4j.LoggerFactory
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.itinerary.dto.ItineraryRequest
import com.tribe.tribe_api.itinerary.dto.ItineraryResponse
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.itinerary.repository.ItineraryItemRepository
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ItineraryService(
    private val itineraryItemRepository: ItineraryItemRepository,
    private val categoryRepository: CategoryRepository,
    private val placeRepository: PlaceRepository,
    private val tripMemberRepository: TripMemberRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun createItinerary(categoryId: Long, request: ItineraryRequest.Create): ItineraryResponse {

        log.info("받은 Category ID는 바로 이것입니다: {}", categoryId)

        val memberId = SecurityUtil.getCurrentMemberId()
        // 파라미터로 받은 categoryId를 사용
        val category = categoryRepository.findById(categoryId)
            .orElseThrow { BusinessException(ErrorCode.CATEGORY_NOT_FOUND) }

        validateTripMember(category.trip.id!!, memberId)

        val place = request.placeId?.let {
            placeRepository.findById(it)
                .orElseThrow { BusinessException(ErrorCode.PLACE_NOT_FOUND) }
        }

        val lastOrder = itineraryItemRepository.findByCategoryIdOrderByOrderAsc(category.id!!)
            .lastOrNull()?.order ?: 0

        val newItem = ItineraryItem(
            category = category,
            place = place,
            title = request.title,
            startTime = request.startTime,
            endTime = request.endTime,
            order = lastOrder + 1,
            memo = request.memo
        )

        return ItineraryResponse.from(itineraryItemRepository.save(newItem))
    }

    @Transactional(readOnly = true)
    fun getItinerariesByCategory(categoryId: Long): List<ItineraryResponse> {
        return itineraryItemRepository.findByCategoryIdOrderByOrderAsc(categoryId)
            .map { ItineraryResponse.from(it) }
    }

    fun updateItinerary(itemId: Long, request: ItineraryRequest.Update): ItineraryResponse {
        val memberId = SecurityUtil.getCurrentMemberId()
        val item = findItemById(itemId)
        validateTripMember(item.category.trip.id!!, memberId)

        item.apply {
            this.title = request.title
            this.startTime = request.startTime
            this.endTime = request.endTime
            this.memo = request.memo
        }
        return ItineraryResponse.from(item)
    }

    fun deleteItinerary(itemId: Long) {
        val memberId = SecurityUtil.getCurrentMemberId()
        val item = findItemById(itemId)
        validateTripMember(item.category.trip.id!!, memberId)

        itineraryItemRepository.delete(item)
    }

    fun updateItineraryOrder(tripId: Long, request: ItineraryRequest.OrderUpdate): List<ItineraryResponse> {
        val memberId = SecurityUtil.getCurrentMemberId()
        validateTripMember(tripId, memberId)

        val itemIds = request.items.map { it.itemId }
        val itemsToUpdate = itineraryItemRepository.findByIdInAndTripId(itemIds, tripId)

        if (itemsToUpdate.size != itemIds.size) {
            throw BusinessException(ErrorCode.ITEM_NOT_FOUND)
        }

        val itemMap = itemsToUpdate.associateBy { it.id }
        request.items.forEach {
            val item = itemMap[it.itemId]!!
            item.order = it.order
        }

        return itemsToUpdate.sortedBy { it.order }.map { ItineraryResponse.from(it) }
    }

    private fun findItemById(itemId: Long): ItineraryItem {
        return itineraryItemRepository.findById(itemId)
            .orElseThrow { BusinessException(ErrorCode.ITEM_NOT_FOUND) }
    }

    private fun validateTripMember(tripId: Long, memberId: Long) {
        // TODO: TripMemberRepository에 실제 여행 멤버인지 확인하는 로직 구현 필요
        // 예시: if (!tripMemberRepository.existsByTripIdAndMemberId(tripId, memberId)) {
        //     throw BusinessException(ErrorCode.NO_AUTHORITY_TRIP)
        // }
    }
}

