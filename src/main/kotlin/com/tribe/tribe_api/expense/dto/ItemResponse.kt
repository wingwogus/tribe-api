package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal

data class ItemResponse(
    val itemId: Long,
    val itemName: String,
    val price: BigDecimal
)