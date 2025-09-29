package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.itinerary.dto.CategoryDto
import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.repository.CategoryRepository
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CategoryService (
    private val categoryRepository: CategoryRepository,
    private val tripRepository: TripRepository
){
    fun createCategory(tripId: Long, request: CategoryDto.CreateRequest): CategoryDto.CategoryResponse {
        val trip = tripRepository.findById(tripId).orElseThrow { EntityNotFoundException("Trip not found") }
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
        val category = categoryRepository.findById(categoryId).orElseThrow { EntityNotFoundException("Category not found") }
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

    fun updateCategory(categoryId: Long, request: CategoryDto.UpdateRequest): CategoryDto.CategoryResponse {
        val category = categoryRepository.findById(categoryId).orElseThrow { EntityNotFoundException("Category not found") }

        request.name?.let { category.name = it }
        request.day?.let { category.day = it }
        request.order?.let { category.order = it }
        request.memo?.let { category.memo = it }

        return CategoryDto.CategoryResponse.from(category)
    }

    fun deleteCategory(categoryId: Long) {
        if (!categoryRepository.existsById(categoryId)) {
            throw EntityNotFoundException("Category not found")
        }
        categoryRepository.deleteById(categoryId)
    }
}