package com.tribe.tribe_api.expense.dto

data class ItemAssignment(
    val itemId: Long,
    val participantIds: List<Long>
)