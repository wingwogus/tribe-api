package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal
import java.time.LocalDate

data class ExpenseCreateResponse(
    val expenseId: Long,
    val expenseTitle: String,
    val totalAmount: BigDecimal,
    val payer: MemberInfo,
    val paymentDate: LocalDate,
    val items: List<ItemResponse>
)