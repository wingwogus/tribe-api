package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal

data class MemberDailySummary (
    val memberId: Long,
    val memberName:String,
    val paidAmount: BigDecimal,
    val assignedAmount: BigDecimal
)