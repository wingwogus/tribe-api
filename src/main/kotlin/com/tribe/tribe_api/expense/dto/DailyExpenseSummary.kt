package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal

data class DailyExpenseSummary(
    val expenseId:Long,
    val title: String,
    val payerName: String,
    val totalAmount: BigDecimal
)