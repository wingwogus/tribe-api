package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.socket.SocketDto.EditType
import com.tribe.tribe_api.common.util.socket.SocketDto.TripEvent
import com.tribe.tribe_api.itinerary.dto.CategoryDto
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CategoryService (
    private val categoryRepository: CategoryRepository,
    private val tripRepository: TripRepository,
    private val eventPublisher: ApplicationEventPublisher

){
    private val logger = LoggerFactory.getLogger(javaClass)

    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun createCategory(tripId: Long, request: CategoryDto.CreateRequest): CategoryDto.CategoryResponse {
        val memberId = SecurityUtil.getCurrentMemberId()

        val trip = tripRepository.findById(tripId).orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }
        val category = Category(
            trip = trip,
            name = request.name,
            day = request.day,
            order = request.order
        )
        val savedCategory = categoryRepository.save(category)

        val createdItem = CategoryDto.CategoryResponse.from(savedCategory)


        eventPublisher.publishEvent(
            TripEvent(
                EditType.ADD_CATEGORY,
                tripId,
                memberId,
                createdItem
            )
        )

        logger.info("Category created. Category ID: {}, Trip ID: {}", savedCategory.id, tripId)
        return createdItem
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun getCategory(tripId: Long, categoryId: Long): CategoryDto.CategoryResponse {
        val category = categoryRepository.findById(categoryId).orElseThrow { BusinessException(ErrorCode.CATEGORY_NOT_FOUND) }

        if (category.trip.id != tripId) {
            throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)
        }

        return CategoryDto.CategoryResponse.from(category)
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun getAllCategories(tripId: Long, day: Int?): List<CategoryDto.CategoryResponse> {
        val categories: List<Category> = if (day != null) {
            categoryRepository.findAllByTripIdAndDayOrderByOrderAsc(tripId, day)
        } else {
            categoryRepository.findAllByTripIdOrderByDayAscOrderAsc(tripId)
        }
        return categories.map { CategoryDto.CategoryResponse.from(it) }
    }

    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun updateCategory(
        tripId: Long,
        categoryId: Long,
        request: CategoryDto.UpdateRequest
    ): CategoryDto.CategoryResponse {
        val memberId = SecurityUtil.getCurrentMemberId()

        val category = categoryRepository.findByIdOrNull(categoryId)
            ?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)

        if (category.trip.id != tripId) {
            throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)
        }

        request.name?.let { category.name = it }
        request.day?.let { category.day = it }
        request.order?.let { category.order = it }
        request.memo?.let { category.memo = it }

        val updatedItem = CategoryDto.CategoryResponse.from(category)

        eventPublisher.publishEvent(
            TripEvent(
                EditType.EDIT_CATEGORY,
                tripId,
                memberId,
                updatedItem
            )
        )

        logger.info("Category updated. Category ID: {}, Trip ID: {}", categoryId, tripId)
        return updatedItem
    }

    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun deleteCategory(tripId : Long ,categoryId: Long) {
        val memberId = SecurityUtil.getCurrentMemberId()

        val category = (categoryRepository.findByIdOrNull(categoryId)
            ?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND))

        if (category.trip.id != tripId) {
            throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER)
        }

        categoryRepository.deleteById(categoryId)

        eventPublisher.publishEvent(
            TripEvent(
                EditType.DELETE_CATEGORY,
                tripId,
                memberId,
                categoryId
            )
        )

        logger.info("Category deleted. Category ID: {}, Trip ID: {}", categoryId, tripId)
    }

    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun orderUpdateCategory(tripId: Long, request : CategoryDto.OrderUpdate) : List<CategoryDto.CategoryResponse> {
        val memberId = SecurityUtil.getCurrentMemberId()

        val requestItems = request.items

        val categoryIds = request.items.map { it.categoryId }

        // 요청받은 ID와 새 order 값을 Map으로 변환
        val newOrderMap = request.items.associateBy({ it.categoryId }, { it.order })

        if (newOrderMap.size != requestItems.size) {
            throw BusinessException(ErrorCode.DUPLICATE_CATEGORY_ID_REQUEST)
        }

        val uniqueOrders = newOrderMap.values.toSet()
        if (uniqueOrders.size != newOrderMap.size) {
            throw BusinessException(ErrorCode.DUPLICATE_ORDER_REQUEST)
        }

        // DB에서 해당 여행(tripId)에 속한 카테고리 중, 요청받은 ID 목록에 해당하는 것들만 조회
        val categoriesToUpdate: List<Category> =
            // day가 있는 경우: tripId, id 목록 모두 일치하는 것 조회
            categoryRepository.findAllByTripIdAndIdIn(tripId, categoryIds)

        categoriesToUpdate.firstOrNull()?.day?.let { firstDay ->
            // day가 다른 항목을 하나라도 찾으면 즉시 true를 반환하고 중단
            if (categoriesToUpdate.any { it.day != firstDay }) {
                throw BusinessException(ErrorCode.CATEGORY_DAY_MISMATCH)
            }
        }

        if (categoriesToUpdate.size != newOrderMap.size) {
            throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)
        }

        // 조회된 카테고리들의 order 값을 Map에 있는 새 order 값으로 업데이트
        categoriesToUpdate.forEach { category ->
            val newOrder = newOrderMap[category.id]!!
            category.updateOrder(newOrder)
        }

        val updatedItems = categoriesToUpdate
            .sortedBy { it.order }
            .map { CategoryDto.CategoryResponse.from(it) }

        eventPublisher.publishEvent(
            TripEvent(
                EditType.MOVE_CATEGORY,
                tripId,
                memberId,
                updatedItems
            )
        )

        //순서가 변경된 카테고리 목록을 다시 조회하여 반환
        logger.info("Category order updated for tripId: {}. Number of categories updated: {}", tripId, categoriesToUpdate.size)
        return updatedItems
    }
}
