package com.tribe.tribe_api.expense.dto

data class AddGuestResponse(
    val participantId: Long,
    val name: String,
    val isGuest: Boolean
)