package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal
import java.time.LocalDate

object SettlementDto {

    data class DailyResponse(
        val date: LocalDate,
        val dailyTotalAmount: BigDecimal,
        val expenses: List<DailyExpenseSummary>,
        val memberSummaries: List<MemberDailySummary>
    )

    data class DailyExpenseSummary(
        val expenseId: Long,
        val title: String,
        val payerName: String,
        val totalAmount: BigDecimal
    )

    data class MemberDailySummary(
        val memberId: Long,
        val memberName: String,
        val paidAmount: BigDecimal,
        val assignedAmount: BigDecimal
    )

    data class TotalResponse(
        val memberBalances: List<MemberBalance>,
        val debtRelations: List<DebtRelation>
    )

    data class MemberBalance(
        val memberId: Long,
        val memberName: String,
        val balance: BigDecimal
    )

    data class DebtRelation(
        val fromMemberName: String,
        val fromMemberId: Long,
        val toMemberName: String,
        val toMemberId: Long,
        val amount: BigDecimal
    )

}