package com.tribe.tribe_api.expense.dto

import java.time.LocalDate
import java.math.BigDecimal

data class ExpenseCreateRequest (
    val expenseTitle: String,
    val totalAmount: BigDecimal,
    val receiptImageUrl: String?,
    val payerId: Long,
    val paymentDate: LocalDate,
    val inputMethod: String,
    val items: List<ItemCreate>
)