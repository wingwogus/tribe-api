package com.tribe.tribe_api.itinerary.dto

import com.tribe.tribe_api.itinerary.entity.ItineraryItem
import com.tribe.tribe_api.itinerary.entity.Place
import java.time.LocalDateTime

data class ItineraryResponse(
    val itineraryId: Long,
    val categoryId: Long,
    //  placeName과 title을 name 하나로 통일
    val name: String,
    val time: LocalDateTime?,
    val order: Int,
    val memo: String?,
    // location은 장소가 있을 때만 값이 있어서 nullable로 변경
    val location: LocationInfo?
) {
    companion object {
        fun from(item: ItineraryItem): ItineraryResponse {
            return ItineraryResponse(
                itineraryId = item.id!!,
                categoryId = item.category.id!!,
                // place가 있으면 place.name을, 없으면 title을 이름으로 사용
                name = item.place?.name ?: item.title!!,
                time = item.time,
                order = item.order,
                memo = item.memo,
                // place가 있을 때만 location 정보를 생성하고, 없으면 null을 보냄
                location = item.place?.let { LocationInfo.from(it) }
            )
        }
    }
}


data class LocationInfo(
    val lat: Double,
    val lng: Double,
    val address: String?
) {
    companion object {

        // Place 엔티티를 LocationInfo DTO로 변환
        fun from(place: Place): LocationInfo {
            return LocationInfo(
                lat = place.latitude.toDouble(),
                lng = place.longitude.toDouble(),
                address = place.address
            )
        }
    }
}

