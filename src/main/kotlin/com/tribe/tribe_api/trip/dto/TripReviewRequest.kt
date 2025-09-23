package com.tribe.tribe_api.trip.dto

sealed class TripReviewRequest {

    data class CreateReview(val concept: String? = null) {}
}