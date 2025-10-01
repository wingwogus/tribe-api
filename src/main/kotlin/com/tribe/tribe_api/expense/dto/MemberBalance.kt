package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal

data class MemberBalance(
    val memberId: Long,
    val memberName: String,
    val balance: BigDecimal
)