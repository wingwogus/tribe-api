package com.tribe.tribe_api.itinerary.dto

import com.tribe.tribe_api.itinerary.entity.Place

object PlaceDto {
    data class Simple(
        val placeId: Long? = null,
        val externalPlaceId: String,
        val placeName: String,
        val address: String,
        val latitude: Double,
        val longitude: Double
    ) {
        companion object {
            fun fromGoogle(googlePlace: GoogleDto.GoogleApiResponse.PlaceResult): Simple {
                return Simple(
                    externalPlaceId = googlePlace.id,
                    placeName = googlePlace.displayName?.text ?: "이름 없음",
                    address = googlePlace.formattedAddress ?: "주소 정보 없음",
                    latitude = googlePlace.location?.latitude ?: 0.0,
                    longitude = googlePlace.location?.longitude ?: 0.0
                )
            }

            fun from(place: Place): Simple {
                return Simple(
                    placeId = place.id,
                    externalPlaceId = place.externalPlaceId,
                    placeName = place.name,
                    address = place.address ?: "주소 정보 없음",
                    latitude = place.latitude.toDouble(),
                    longitude = place.longitude.toDouble()
                )
            }
        }
    }
}
