package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal
import java.time.LocalDate

data class ExpenseUpdateRequest(
    val expenseTitle: String,
    val totalAmount: BigDecimal,
    val paymentDate: LocalDate,
    val payerId: Long,
    val items: List<ItemUpdateRequest>
)