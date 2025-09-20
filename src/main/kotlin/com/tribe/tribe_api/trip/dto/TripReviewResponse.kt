package com.tribe.tribe_api.trip.dto

import com.tribe.tribe_api.trip.entity.TripReview
import java.time.LocalDateTime

sealed class TripReviewResponse() {

    data class ReviewDetail(
        val reviewId: Long,
        val content: String?,
        val createdAt: LocalDateTime?
    ) {
        companion object {
            fun from(review: TripReview): ReviewDetail {
                return ReviewDetail(
                    reviewId = review.id!!,
                    content = review.content,
                    createdAt = review.createdAt
                )
            }
        }
    }
}