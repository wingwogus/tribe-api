package com.tribe.tribe_api.expense.dto

object TripMemberDto {
    data class AddGuestRequest(
        val name: String
    )

    data class AddGuestResponse(
        val participantId: Long,
        val name: String,
        val isGuest: Boolean
    )
}