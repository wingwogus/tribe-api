package com.tribe.tribe_api.itinerary.dto

import com.tribe.tribe_api.itinerary.entity.Category
import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import java.time.LocalDateTime

object CategoryDto {

    data class CreateRequest(
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
        val itineraryItems: List<ItineraryItemResponse>,
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
                    itineraryItems = category.itineraryItems.map { ItineraryItemResponse.from(it) },
                    memo = category.memo,
                    createdAt = category.createdAt,
                    updatedAt = category.updatedAt
                )
            }
        }

        data class ItineraryItemResponse(
            val itemId: Long,
            val placeName: String?,
            val order: Int,
            val memo: String?
        ) {
            companion object {
                fun from(item: ItineraryItem): ItineraryItemResponse {
                    return ItineraryItemResponse(
                        itemId = item.id!!,
                        placeName = item.place?.name,
                        order = item.order,
                        memo = item.memo
                    )
                }
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