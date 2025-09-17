package com.tribe.tribe_api.itinerary.dto

object PlaceDto {
    data class SearchResponse(
        val places: List<Simple>,
        val nextPageToken: String?
    )

    data class Simple(
        val placeId: String,
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double
    ) {
        companion object {
            // Google API 응답 객체를 우리 DTO로 변환하는 팩토리 메서드
            fun from(googleResult: GoogleDto.Response.PlaceResult): Simple {
                return Simple(
                    placeId = googleResult.placeId,
                    name = googleResult.name,
                    address = googleResult.formattedAddress,
                    latitude = googleResult.geometry.location.lat,
                    longitude = googleResult.geometry.location.lng
                )
            }
        }
    }
}