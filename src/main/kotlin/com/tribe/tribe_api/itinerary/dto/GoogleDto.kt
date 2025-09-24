package com.tribe.tribe_api.itinerary.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

object GoogleDto {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GoogleApiResponse(
        val places: List<PlaceResult>?,
    ) {
        data class PlaceResult(
            val id: String,
            val formattedAddress: String?,
            val location: Location?,
            val displayName: DisplayName?,
        )

        data class Location(
            val latitude: Double,
            val longitude: Double
        )

        data class DisplayName(
            val text: String,
            val languageCode: String
        )
    }
}