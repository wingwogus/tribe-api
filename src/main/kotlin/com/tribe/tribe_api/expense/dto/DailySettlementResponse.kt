package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal
import java.time.LocalDate

data class DailySettlementResponse(
    val date: LocalDate,
    val dailyTotalAmount: BigDecimal,
    val expenses: List<DailyExpenseSummary>,
    val memberSummaries: List<MemberDailySummary>
)