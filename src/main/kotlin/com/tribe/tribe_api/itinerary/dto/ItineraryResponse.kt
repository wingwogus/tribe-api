package com.tribe.tribe_api.itinerary.dto

import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.entity.Place
import java.time.LocalDateTime

data class ItineraryResponse(
    val itemId: Long,
    val categoryId: Long,
    val title: String,
    val startTime: LocalDateTime?,
    val endTime: LocalDateTime?,
    val order: Int,
    val memo: String?,
    val place: PlaceInfo?
) {
    companion object {
        fun from(item: ItineraryItem): ItineraryResponse {
            return ItineraryResponse(
                itemId = item.id!!,
                categoryId = item.category.id!!,
                title = item.title,
                startTime = item.startTime,
                endTime = item.endTime,
                order = item.order,
                memo = item.memo,
                place = item.place?.let { PlaceInfo.from(it) }
            )
        }
    }
}

data class PlaceInfo(
    val placeId: Long,
    val name: String,
    val address: String?
) {
    companion object {
        fun from(place: Place): PlaceInfo {
            return PlaceInfo(
                placeId = place.id!!,
                name = place.name,
                address = place.address
            )
        }
    }
}
