package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal

data class ItemCreate(
    val itemName: String,
    val price: BigDecimal
)