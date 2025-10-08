package com.tribe.tribe_api.trip.dto

import com.tribe.tribe_api.trip.entity.TripReview
import java.time.LocalDateTime

sealed class TripReviewResponse() {

    data class ReviewDetail(
        val reviewId: Long,
        val concept: String?,
        val content: String?,
        val createdAt: LocalDateTime?
    ) {
        companion object {
            fun from(review: TripReview): ReviewDetail {
                return ReviewDetail(
                    reviewId = review.id!!,
                    concept = review.concept,
                    content = review.content,
                    createdAt = review.createdAt
                )
            }
        }
    }

    data class SimpleReviewInfo(
        val reviewId: Long,
        val title: String?,
        val concept: String?,
        val createdAt: LocalDateTime?
    ) {
        companion object {
            fun from(review: TripReview): SimpleReviewInfo {
                return SimpleReviewInfo(
                    reviewId = review.id!!,
                    title = extractTitle(review.content),
                    concept = review.concept,
                    createdAt = review.createdAt
                )
            }

            fun extractTitle(text: String): String? {
                if (text.startsWith("## ")) {
                    val contentAfterPrefix = text.substringAfter("## ")
                    return contentAfterPrefix.substringBefore("\n")
                }
                return null
            }

        }
    }
}