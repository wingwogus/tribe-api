package com.tribe.tribe_api.itinerary.dto

import com.tribe.tribe_api.itinerary.entity.Category
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
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
        val itineraryItems: List<ItineraryResponse.ItineraryDetail>,
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
                    itineraryItems = category.itineraryItems.map { ItineraryResponse.ItineraryDetail.from(it) },
                    memo = category.memo,
                    createdAt = category.createdAt,
                    updatedAt = category.lastModifiedAt
                )
            }
        }
    }

    data class UpdateRequest(
        val name: String? = null,
        val day: Int? = null,
        val order: Int? = null,
        val memo: String? = null
    )

    data class OrderUpdate(
        @field:Valid
        @field:NotNull
        val items: List<OrderCategory>
    )

    data class OrderCategory(
        @field:NotNull
        val categoryId: Long,

        @field:NotNull
        val order: Int
    )
}