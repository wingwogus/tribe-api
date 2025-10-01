package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal
import java.time.LocalDate

data class ExpenseDetailResponse(
    val expenseId: Long,
    val expenseTitle: String,
    val totalAmount: BigDecimal,
    val paymentDate: LocalDate,
    val payer: MemberInfo,
    val items: List<ItemInfo>
)