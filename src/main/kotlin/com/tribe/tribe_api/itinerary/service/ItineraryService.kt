package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.itinerary.dto.ItineraryRequest
import com.tribe.tribe_api.itinerary.dto.ItineraryResponse
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.itinerary.repository.ItineraryItemRepository
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import org.slf4j.LoggerFactory
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
        log.info("일정 생성 요청. Category ID: {}", categoryId)
        val memberId = SecurityUtil.getCurrentMemberId()
        val category = categoryRepository.findById(categoryId)
            .orElseThrow { BusinessException(ErrorCode.CATEGORY_NOT_FOUND) }

        validateTripMember(category.trip.id!!, memberId)

        var place: Place? = null
        var title: String? = null

        if (request.placeId != null) {
            // placeId가 있는 경우, 장소 기반 일정
            place = placeRepository.findById(request.placeId)
                .orElseThrow { BusinessException(ErrorCode.PLACE_NOT_FOUND) }
            title = null // title은 사용하지 않음
        } else if (!request.title.isNullOrBlank()) {
            title = request.title
            place = null // place는 사용하지 않음
        } else {
            throw BusinessException(ErrorCode.INVALID_INPUT_VALUE)
        }

        val lastOrder = itineraryItemRepository.findByCategoryIdOrderByOrderAsc(category.id!!)
            .lastOrNull()?.order ?: 0

        val newItem = ItineraryItem(
            category = category,
            place = place,
            title = title,
            time = request.time,
            order = lastOrder + 1,
            memo = request.memo
        )

        return ItineraryResponse.from(itineraryItemRepository.save(newItem))
    }

    // 카테고리별 모든 일정 조회

    @Transactional(readOnly = true)
    // tripId를 파라미터로 추가
    fun getItinerariesByCategory(tripId: Long, categoryId: Long): List<ItineraryResponse> {

        val memberId = SecurityUtil.getCurrentMemberId()
        validateTripMember(tripId, memberId)

        val category = categoryRepository.findById(categoryId)
            .orElseThrow { BusinessException(ErrorCode.CATEGORY_NOT_FOUND) }

        if (category.trip.id != tripId) {
            throw BusinessException(ErrorCode.NO_BELONG_TRIP)
        }

        // 검증이 끝난 뒤 카테고리 ID로 일정 목록을 조회
        return itineraryItemRepository.findByCategoryIdOrderByOrderAsc(categoryId)
            .map { ItineraryResponse.from(it) }
    }

    // 특정 일정 정보 수정

    fun updateItinerary(itemId: Long, request: ItineraryRequest.Update): ItineraryResponse {
        val memberId = SecurityUtil.getCurrentMemberId()
        val item = findItemById(itemId)

        validateTripMember(item.category.trip.id!!, memberId)

        item.apply {
            this.time = request.time
            this.memo = request.memo
        }
        return ItineraryResponse.from(item)
    }

    // 특정 일정 삭제

    fun deleteItinerary(itemId: Long) {
        val memberId = SecurityUtil.getCurrentMemberId()
        val item = findItemById(itemId)

        validateTripMember(item.category.trip.id!!, memberId)

        itineraryItemRepository.delete(item)
    }

    // 전체 일정 순서 일괄 변경
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
        if (!tripMemberRepository.existsByTripIdAndMemberId(tripId, memberId)) {
            throw BusinessException(ErrorCode.NO_AUTHORITY_TRIP)
        }
    }
}

