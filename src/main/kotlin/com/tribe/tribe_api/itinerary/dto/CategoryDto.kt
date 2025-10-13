package com.tribe.tribe_api.itinerary.dto

import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

object CategoryDto {

    data class CreateRequest(
        @field:NotBlank(message = "카테고리 이름은 비워둘 수 없습니다.")
        val name: String,
        val day: Int,
        val order: Int
    )

    data class CategoryResponse(
        val categoryId: Long,
        val name: String,
        val day: Int,
        val order: Int,
        val tripId: Long,
        val itineraryItems: List<ItineraryResponse>,
        val memo: String?,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime
    ) {
        companion object {
            fun from(category: Category): CategoryResponse {
                return CategoryResponse(
                    categoryId = category.id!!,
                    name = category.name,
                    day = category.day,
                    order = category.order,
                    tripId = category.trip.id!!,
                    itineraryItems = category.itineraryItems.map { ItineraryResponse.from(it) },
                    memo = category.memo,
                    createdAt = category.createdAt,
                    updatedAt = category.lastModifiedAt
                )
            }
        }
    }

    data class UpdateRequest(
        val name: String?,
        val day: Int?,
        val order: Int?,
        val memo: String?
    )
}