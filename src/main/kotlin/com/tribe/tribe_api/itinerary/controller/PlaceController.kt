package com.tribe.tribe_api.itinerary.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.common.util.service.GoogleMapService
import com.tribe.tribe_api.itinerary.dto.PlaceDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/places")
class PlaceController(
    private val googleMapService: GoogleMapService
) {
    @GetMapping("/search")
    fun searchPlaces(
        @RequestParam query: String?,
        @RequestParam region: String?,
        @RequestParam(defaultValue = "ko") language: String

    ): ResponseEntity<ApiResponse<List<PlaceDto.Simple>>> {


        val response = googleMapService.searchPlaces(query, language, region)

        return ResponseEntity.ok(ApiResponse.success(response))
    }
}