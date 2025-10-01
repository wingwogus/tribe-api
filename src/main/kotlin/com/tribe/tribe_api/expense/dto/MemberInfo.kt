package com.tribe.tribe_api.expense.dto

data class MemberInfo(
    val id: Long,
    val name: String,
    val isGuest: Boolean
)