package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.itinerary.dto.CategoryDto
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.trip.repository.TripMemberRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CategoryService (
    private val categoryRepository: CategoryRepository,
    private val tripRepository: TripRepository,
    private val tripMemberRepository: TripMemberRepository
){
    fun createCategory(tripId: Long, request: CategoryDto.CreateRequest): CategoryDto.CategoryResponse {
        val trip = tripRepository.findById(tripId).orElseThrow { BusinessException(ErrorCode.TRIP_NOT_FOUND) }
        val category = Category(
            trip = trip,
            name = request.name,
            day = request.day,
            order = request.order
        )
        val savedCategory = categoryRepository.save(category)
        return CategoryDto.CategoryResponse.from(savedCategory)
    }

    @Transactional(readOnly = true)
    fun getCategory(categoryId: Long): CategoryDto.CategoryResponse {
        val category = categoryRepository.findById(categoryId).orElseThrow { BusinessException(ErrorCode.CATEGORY_NOT_FOUND) }
        return CategoryDto.CategoryResponse.from(category)
    }

    @Transactional(readOnly = true)
    fun getAllCategories(tripId: Long, day: Int?): List<CategoryDto.CategoryResponse> {
        val categories: List<Category> = if (day != null) {
            categoryRepository.findAllByTripIdAndDayOrderByOrderAsc(tripId, day)
        } else {
            categoryRepository.findAllByTripIdOrderByDayAscOrderAsc(tripId)
        }
        return categories.map { CategoryDto.CategoryResponse.from(it) }
    }

    fun updateCategory(
        categoryId: Long,
        request: CategoryDto.UpdateRequest
    ): CategoryDto.CategoryResponse {

        val memberId = SecurityUtil.getCurrentMemberId()

        val category = categoryRepository.findByIdOrNull(categoryId)
            ?: throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)

        val isTripMember = tripMemberRepository.existsByTripIdAndMemberId(category.trip.id!!, memberId)
        if (!isTripMember) {
            throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER)
        }

        request.name?.let { category.name = it }
        request.day?.let { category.day = it }
        request.order?.let { category.order = it }
        request.memo?.let { category.memo = it }

        return CategoryDto.CategoryResponse.from(category)
    }

    fun deleteCategory(tripId : Long ,categoryId: Long) {
        val memberId = SecurityUtil.getCurrentMemberId()

        val isTripMember = tripMemberRepository.existsByTripIdAndMemberId(tripId, memberId)
        if (!isTripMember) {
            throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER)
        }

        if (!categoryRepository.existsById(categoryId)) {
            throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)
        }
        categoryRepository.deleteById(categoryId)
    }

    fun orderUpdateCategory(tripId: Long, day: Int? ,request : CategoryDto.OrderUpdate) : List<CategoryDto.CategoryResponse> {
        val memberId = SecurityUtil.getCurrentMemberId()


        val requestItems = request.items

        val isTripMember = tripMemberRepository.existsByTripIdAndMemberId(tripId, memberId)
        if (!isTripMember) {
            throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER)
        }

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
        val categoriesToUpdate: List<Category> = if (day != null) {
            // day가 있는 경우: tripId, day, id 목록 모두 일치하는 것 조회
            categoryRepository.findAllByTripIdAndDayAndIdIn(tripId, day, categoryIds)
        } else {
            // day가 null인 경우: tripId, day가 null, id 목록 모두 일치하는 것 조회
            categoryRepository.findAllByTripIdAndDayIsNullAndIdIn(tripId, categoryIds)
        }

        if (categoriesToUpdate.size != newOrderMap.size) {
            throw BusinessException(ErrorCode.CATEGORY_NOT_FOUND)
        }

        // 조회된 카테고리들의 order 값을 Map에 있는 새 order 값으로 업데이트
        categoriesToUpdate.forEach { category ->
            val newOrder = newOrderMap[category.id]!!
            category.updateOrder(newOrder)
        }
        //순서가 변경된 카테고리 목록을 다시 조회하여 반환
        return categoriesToUpdate
            .sortedBy { it.order }
            .map { CategoryDto.CategoryResponse.from(it) }
    }
}