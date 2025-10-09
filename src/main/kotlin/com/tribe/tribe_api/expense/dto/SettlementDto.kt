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

    // 변경내역있습니다. Hyeni
    data class MemberBalance(
        val tripMemberId: Long, // memberId -> tripMemberId
        val nickname: String,   // memberName -> nickname
        val balance: BigDecimal
    )

    data class DebtRelation(
        val fromNickname: String,      // fromMemberName -> fromNickname
        val fromTripMemberId: Long,  // fromMemberId -> fromTripMemberId
        val toNickname: String,        // toMemberName -> toNickname
        val toTripMemberId: Long,    // toMemberId -> toTripMemberId
        val amount: BigDecimal
    )
}