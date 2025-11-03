package com.tribe.tribe_api.itinerary.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.itinerary.dto.ItineraryRequest
import com.tribe.tribe_api.itinerary.dto.ItineraryResponse
import com.tribe.tribe_api.itinerary.entity.TravelMode
import com.tribe.tribe_api.itinerary.service.ItineraryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/trips/{tripId}")
class ItineraryController(
    private val itineraryService: ItineraryService
) {

    @PostMapping("/categories/{categoryId}/itineraries")
    fun createItinerary(
        @PathVariable tripId: Long,
        @PathVariable categoryId: Long, // 이 값을
        @Valid @RequestBody request: ItineraryRequest.Create
    ): ResponseEntity<ApiResponse<ItineraryResponse.ItineraryDetail>> {
        // 서비스 호출 시, categoryId를 첫 번째 인자로 전달
        val response = itineraryService.createItinerary(categoryId, request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("일정 생성 성공", response))
    }

    @GetMapping("/categories/{categoryId}/itineraries")
    fun getItinerariesByCategory(
        @PathVariable categoryId: Long,
        @PathVariable tripId: Long
    ): ResponseEntity<ApiResponse<List<ItineraryResponse.ItineraryDetail>>> {
        val response = itineraryService.getItinerariesByCategory(tripId, categoryId)
        return ResponseEntity.ok(ApiResponse.success("카테고리별 일정 조회 성공", response))
    }

    @PatchMapping("/categories/{categoryId}/itineraries/{itemId}")
    fun updateItinerary(
        @PathVariable itemId: Long,
        @PathVariable tripId: Long,
        @Valid @RequestBody request: ItineraryRequest.Update
    ): ResponseEntity<ApiResponse<ItineraryResponse.ItineraryDetail>> {
        val response = itineraryService.updateItinerary(itemId, request)
        return ResponseEntity.ok(ApiResponse.success("일정 수정 성공", response))
    }

    @DeleteMapping("/categories/{categoryId}/itineraries/{itemId}")
    fun deleteItinerary(
        @PathVariable itemId: Long,
        @PathVariable tripId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        itineraryService.deleteItinerary(itemId)
        return ResponseEntity.ok(ApiResponse.success("일정 삭제 성공", null))
    }

    @PatchMapping("/itineraries/order")
    fun updateItineraryOrder(
        @PathVariable tripId: Long,
        @Valid @RequestBody request: ItineraryRequest.OrderUpdate
    ): ResponseEntity<ApiResponse<List<ItineraryResponse.ItineraryDetail>>> {
        val response = itineraryService.updateItineraryOrder(tripId, request)
        return ResponseEntity.ok(ApiResponse.success("일정 순서 변경 성공", response))
    }

    @GetMapping("/itineraries/directions/all")
    fun getAllDirections(
        @PathVariable tripId: Long,
        @RequestParam mode: String
    ): ResponseEntity<ApiResponse<List<ItineraryResponse.RouteDetails>>> {
        val response = itineraryService.getAllDirectionsForTrip(
            tripId,
            TravelMode.valueOf(mode.uppercase(Locale.ROOT)))
        return ResponseEntity.ok(ApiResponse.success("전체 경로 조회 성공", response))
    }
}

