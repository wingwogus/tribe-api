package com.tribe.tribe_api.itinerary.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

sealed class ItineraryRequest {

    data class Create(
        @field:NotNull(message = "카테고리 ID는 필수입니다.")
        val categoryId: Long,

        val placeId: Long?,

        @field:NotBlank(message = "일정 제목은 필수입니다.")
        val title: String,

        val startTime: LocalDateTime?,
        val endTime: LocalDateTime?,
        val memo: String?
    )

    data class Update(
        @field:NotBlank(message = "일정 제목은 필수입니다.")
        val title: String,

        val startTime: LocalDateTime?,
        val endTime: LocalDateTime?,
        val memo: String?
    )

    data class OrderUpdate(
        @field:NotNull
        val items: List<OrderItem>
    )

    data class OrderItem(
        @field:NotNull
        val itemId: Long,
        @field:NotNull
        val order: Int
    )
}
