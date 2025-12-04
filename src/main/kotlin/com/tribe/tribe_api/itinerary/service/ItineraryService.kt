package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.service.GoogleMapService
import com.tribe.tribe_api.common.util.socket.SocketDto.EditType
import com.tribe.tribe_api.common.util.socket.SocketDto.TripEvent
import com.tribe.tribe_api.itinerary.dto.ItineraryRequest
import com.tribe.tribe_api.itinerary.dto.ItineraryResponse
import com.tribe.tribe_api.itinerary.dto.PlaceDto
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.entity.Place
import com.tribe.tribe_api.itinerary.entity.TravelMode
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.itinerary.repository.ItineraryItemRepository
import com.tribe.tribe_api.itinerary.repository.PlaceRepository
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ItineraryService(
    private val itineraryItemRepository: ItineraryItemRepository,
    private val categoryRepository: CategoryRepository,
    private val placeRepository: PlaceRepository,
    private val tripMemberRepository: TripMemberRepository,
    private val googleMapService: GoogleMapService,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun createItinerary(categoryId: Long, request: ItineraryRequest.Create): ItineraryResponse.ItineraryDetail {
        logger.info("Itinerary creation requested. Category ID: {}", categoryId)
        val memberId = SecurityUtil.getCurrentMemberId()
        val category = categoryRepository.findById(categoryId)
            .orElseThrow { BusinessException(ErrorCode.CATEGORY_NOT_FOUND) }

        val tripId = category.trip.id!!

        validateTripMember(tripId, memberId)

        var place: Place?
        var title: String?

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

        // 현재 카테고리의 전체 아이템 개수를 셈
        val currentItemCount = itineraryItemRepository.countByCategoryId(category.id!!)

        val newItem = ItineraryItem(
            category = category,
            place = place,
            title = title,
            time = request.time,
            // 새 아이템의 순서는 (현재 개수 + 1)
            order = currentItemCount + 1,
            memo = request.memo
        )

        val savedItem = itineraryItemRepository.save(newItem)
        val createdItem = ItineraryResponse.ItineraryDetail.from(savedItem)

        eventPublisher.publishEvent(
            TripEvent(
                EditType.ADD_ITINERARY,
                tripId,
                memberId,
                createdItem
            )
        )

        logger.info("Itinerary item created. Item ID: {}", savedItem.id)

        return createdItem
    }

    // 카테고리별 모든 일정 조회

    @Transactional(readOnly = true)
    // tripId를 파라미터로 추가
    fun getItinerariesByCategory(tripId: Long, categoryId: Long): List<ItineraryResponse.ItineraryDetail> {
        val memberId = SecurityUtil.getCurrentMemberId()
        validateTripMember(tripId, memberId)

        val category = categoryRepository.findById(categoryId)
            .orElseThrow { BusinessException(ErrorCode.CATEGORY_NOT_FOUND) }

        if (category.trip.id != tripId) {
            throw BusinessException(ErrorCode.NO_BELONG_TRIP)
        }

        // 검증이 끝난 뒤 카테고리 ID로 일정 목록을 조회
        return itineraryItemRepository.findByCategoryIdOrderByOrderAsc(categoryId)
            .map { ItineraryResponse.ItineraryDetail.from(it) }
    }

    // 특정 일정 정보 수정

    fun updateItinerary(itemId: Long, request: ItineraryRequest.Update): ItineraryResponse.ItineraryDetail {
        val memberId = SecurityUtil.getCurrentMemberId()
        val item = findItemById(itemId)

        val tripId = item.category.trip.id!!
        validateTripMember(tripId, memberId)

        item.apply {
            this.time = request.time
            this.memo = request.memo
        }

        val updatedItem = ItineraryResponse.ItineraryDetail.from(item)

        eventPublisher.publishEvent(
            TripEvent(
                EditType.EDIT_ITINERARY,
                tripId,
                memberId,
                updatedItem
            )
        )

        logger.info("Itinerary item updated. Item ID: {}", item.id)
        return updatedItem
    }

    // 특정 일정 삭제
    fun deleteItinerary(itemId: Long) {
        val memberId = SecurityUtil.getCurrentMemberId()
        val item = findItemById(itemId)

        val tripId = item.category.trip.id!!

        validateTripMember(tripId, memberId)

        itineraryItemRepository.delete(item)

        eventPublisher.publishEvent(
            TripEvent(
                EditType.DELETE_ITINERARY,
                tripId,
                memberId,
                itemId
            )
        )

        logger.info("Itinerary item deleted. Item ID: {}", itemId)
    }

    // 전체 일정 순서 일괄 변경
    fun updateItineraryOrder(tripId: Long, request: ItineraryRequest.OrderUpdate): List<ItineraryResponse.ItineraryDetail> {
        val memberId = SecurityUtil.getCurrentMemberId()
        validateTripMember(tripId, memberId)

        // 요청 데이터 자체의 검증 로직 추가, 동일한 카테고리 내에 중복된 order 값이 있는지 확인
        val hasDuplicateOrders = request.items
            .groupBy { it.categoryId } // categoryId 별로 그룹을 만듬
            .any { (_, itemsInCategory) -> // 각 그룹(카테고리)에 대해 검사
                val orders = itemsInCategory.map { it.order } // 해당 카테고리의 order 목록을 추출
                orders.size != orders.toSet().size // 목록의 크기와 Set(중복제거)의 크기가 다르면 중복이 있다는 뜻
            }

        if (hasDuplicateOrders) {
            throw BusinessException(ErrorCode.INVALID_INPUT_VALUE)
        }


        val itemIds = request.items.map { it.itemId }
        val itemsToUpdate = itineraryItemRepository.findByIdInAndTripId(itemIds, tripId)

        // 요청에 포함된 모든 카테고리 ID를 미리 조회하여 Map으로 만듬
        val categoryIds = request.items.map { it.categoryId }.distinct()
        val categoryMap = categoryRepository.findAllById(categoryIds).associateBy { it.id }

        // 모든 요청된 카테고리가 DB에 존재하는지, 그리고 현재 여행에 속하는지 확인
        if (categoryMap.size != categoryIds.size || categoryMap.values.any { it.trip.id != tripId }) {
            throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)
        }

        if (itemsToUpdate.size != itemIds.size) {
            throw BusinessException(ErrorCode.ITEM_NOT_FOUND)
        }

        val itemMap = itemsToUpdate.associateBy { it.id }
        request.items.forEach { orderItem ->
            val item = itemMap[orderItem.itemId]!!
            val newCategory = categoryMap[orderItem.categoryId]!!

            // order와 category를 모두 업데이트합니다.
            item.order = orderItem.order
            item.category = newCategory
        }

        val updatedItems = itemsToUpdate.sortedBy { it.order }.map { ItineraryResponse.ItineraryDetail.from(it) }

        eventPublisher.publishEvent(
            TripEvent(
                EditType.MOVE_ITINERARY,
                tripId,
                memberId,
                updatedItems
            )
        )

        logger.info("Itinerary order updated for tripId: {}. Number of items updated: {}", tripId, itemsToUpdate.size)

        return updatedItems
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

    /**
     * Trip에 속한 일정들간의 모든 거리 계산 후 배열로 반환
     * @param tripId 계산을 원하는 여행의 id
     * @param mode 이동 방법
     */
    fun getAllDirectionsForTrip(
        tripId: Long,
        mode: String
    ): List<ItineraryResponse.RouteDetails> {
        val travelMode = TravelMode.entries
            .firstOrNull { it.name.equals(mode, ignoreCase = true) }
            ?: throw BusinessException(ErrorCode.TRAVEL_MODE_NOT_FOUND)

        val itineraryItems = itineraryItemRepository.findByTripIdOrderByCategoryAndOrder(tripId)

        // 일정이 2개보다 적다면 빈 배열 반환
        if (itineraryItems.size < 2) {
            return emptyList()
        }

        // 일정들를 순서대로 Pair<originItem, destinationItem> 쌍으로 만들어 순환하며 치환
        return itineraryItems.zipWithNext().mapNotNull { (originItem, destinationItem) ->
            if (originItem.place == null || destinationItem.place == null) {
                return@mapNotNull null
            }

            getDirectionBetweenPlaces(
                originItem.place!!,
                destinationItem.place!!,
                travelMode)
        }
    }

    /**
     * mode에 따른 두 장소간 거리 계산 후 DTO로 반환
     * @param originPlace 출발지
     * @param destinationPlace 도착지
     * @param mode 이동 방법
     */
    private fun getDirectionBetweenPlaces(
        originPlace: Place,
        destinationPlace: Place,
        mode: TravelMode
    ): ItineraryResponse.RouteDetails? {
        val routeDetails = googleMapService.getDirections(
            originPlace.externalPlaceId,
            destinationPlace.externalPlaceId,
            mode)
            ?: throw BusinessException(ErrorCode.EXTERNAL_API_ERROR)

        // 1. API 응답 상태 확인 OK일때만 실행
        if (routeDetails.status != "OK") {
            // ZERO_RESULTS 일 떄 null을 반환하여 호출할 때 처리                                                        │
            logger.warn("Google API returned status: {} for origin: {}, destination: {}, mode: {}",
                routeDetails.status,
                originPlace.name,
                destinationPlace.name,
                mode)

            return null
        }

        // 2. 실제 데이터가 있는 routes[0], legs[0] 추출
        val route = routeDetails.routes.firstOrNull()
            ?: return null
        val leg = route.legs.firstOrNull()
            ?: return null

        // 3. List<RawStep> -> List<CleanStep>로 변환
        val cleanSteps = leg.steps.map { rawStep ->

            // 4. RawTransitDetails -> TransitDetails 변환
            val cleanTransitDetails = rawStep.transitDetails?.let { rawTransit ->
                ItineraryResponse.RouteDetails.TransitDetails(
                    lineName = rawTransit.line.shortName ?: "이름 없음",
                    vehicleType = rawTransit.line.vehicle.type,
                    vehicleIconUrl = rawTransit.line.vehicle.icon,
                    numStops = rawTransit.numStops,
                    departureStop = rawTransit.departureStop.name,
                    arrivalStop = rawTransit.arrivalStop.name
                )
            }

            // 5. RawStep -> Clean RouteStep 변환
            ItineraryResponse.RouteDetails.RouteStep(
                travelMode = rawStep.travelMode,
                instructions = rawStep.htmlInstructions.replace(Regex("<[^>]*>"), ""),
                duration = rawStep.duration.text,
                distance = rawStep.distance.text,
                transitDetails = cleanTransitDetails // WALKING 스텝이면 여기가 자동으로 null
            )
        }

        // 6. 최종 DTO 반환
        return ItineraryResponse.RouteDetails(
            travelMode = mode.toString(),
            originPlace = PlaceDto.Simple.from(originPlace),
            destinationPlace = PlaceDto.Simple.from(destinationPlace),
            totalDuration = leg.duration.text,
            totalDistance = leg.distance.text,
//            overviewPolyline = route.overviewPolyline.points,
            steps = cleanSteps
        )
    }
}

