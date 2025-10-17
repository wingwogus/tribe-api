package com.tribe.tribe_api.trip.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.trip.dto.TripReviewRequest
import com.tribe.tribe_api.trip.dto.TripReviewResponse
import com.tribe.tribe_api.trip.service.TripReviewService
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

    @GetMapping
    fun getAllReviews(
        @PathVariable tripId: Long,
        @PageableDefault(size = 10, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable
    ): ResponseEntity<*> {
        val allReviews = tripReviewService.getAllReviews(tripId, pageable)
        return ResponseEntity.ok(
            ApiResponse.success(
                "여행 검토 목록 조회 성공",
                allReviews
            )
        )
    }

    @GetMapping("/{reviewId}")
    fun getReview(
        @PathVariable tripId: Long,
        @PathVariable reviewId: Long,
    ): ResponseEntity<*> {
        val review = tripReviewService.getReview(tripId, reviewId)

        return ResponseEntity.ok(
            ApiResponse.success(
                "여행 검토 단건 조회 성공",
                review
            )
        )
    }
}