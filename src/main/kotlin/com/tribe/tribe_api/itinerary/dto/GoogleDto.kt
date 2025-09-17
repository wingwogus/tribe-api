package com.tribe.tribe_api.itinerary.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

object GoogleDto {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Response(
        val results: List<PlaceResult>,
        val status: String,
        @JsonProperty("next_page_token")
        val nextPageToken: String?
    ) {
        data class PlaceResult(
            @JsonProperty("place_id")
            val placeId: String,
            val name: String,
            @JsonProperty("formatted_address")
            val formattedAddress: String,
            val geometry: Geometry
        )

        data class Geometry(
            val location: Location
        )

        data class Location(
            val lat: Double,
            val lng: Double
        )
    }
}