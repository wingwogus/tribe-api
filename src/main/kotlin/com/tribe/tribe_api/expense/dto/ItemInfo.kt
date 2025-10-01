package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal

data class ItemInfo(
    val itemId: Long,
    val itemName: String,
    val price: BigDecimal,
    val participants: List<MemberInfo>
)