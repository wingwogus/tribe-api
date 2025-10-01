package com.tribe.tribe_api.itinerary.dto

object PlaceDto {
    data class SearchResponse(
        val places: List<Simple>,
        val nextPageToken: String?
    )
    data class Simple(
        val placeId: String,
        val placeName: String,
        val address: String,
        val latitude: Double,
        val longitude: Double
    ) {
        companion object {
            fun from(googlePlace: GoogleDto.GoogleApiResponse.PlaceResult): Simple {
                return Simple(
                    placeId = googlePlace.id,
                    placeName = googlePlace.displayName?.text ?: "이름 없음",
                    address = googlePlace.formattedAddress ?: "주소 정보 없음",
                    latitude = googlePlace.location?.latitude ?: 0.0,
                    longitude = googlePlace.location?.longitude ?: 0.0
                )
            }
        }
    }
}
