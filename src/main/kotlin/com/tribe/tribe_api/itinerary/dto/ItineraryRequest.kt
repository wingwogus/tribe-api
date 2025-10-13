package com.tribe.tribe_api.itinerary.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

sealed class ItineraryRequest {

    data class Create(

        // placeId와 title 모두 선택적으로 받도록 nullable(?)로 변경
        val placeId: Long?,
        val title: String?,

        val time: LocalDateTime?,
        val memo: String?
    )

    // 일정 수정
    data class Update(
        val time: LocalDateTime?,
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
        val categoryId: Long,

        @field:NotNull
        val order: Int
    )
}

