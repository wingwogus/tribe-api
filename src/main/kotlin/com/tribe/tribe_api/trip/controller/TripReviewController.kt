package com.tribe.tribe_api.trip.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.trip.dto.TripReviewRequest
import com.tribe.tribe_api.trip.dto.TripReviewResponse
import com.tribe.tribe_api.trip.service.TripReviewService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/trips/{tripId}/reviews")
class TripReviewController(
    private val tripReviewService: TripReviewService
) {

    @PostMapping
    fun createReview(
        @PathVariable tripId: Long,
        @RequestBody request: TripReviewRequest.CreateReview
    ): ResponseEntity<ApiResponse<TripReviewResponse.ReviewDetail>> {

        val response = tripReviewService.createReview(tripId, request)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse
                .success(
                    "AI 리뷰가 성공적으로 생성되었습니다.",
                    response
                )
            )
    }
}