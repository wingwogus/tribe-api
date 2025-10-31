package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal
import java.time.LocalDate

object SettlementDto {

    data class DailyResponse(
        val date: LocalDate,
        val dailyTotalAmount: BigDecimal,
        val expenses: List<DailyExpenseSummary>,
        val memberSummaries: List<MemberDailySummary>,
        val debtRelations: List<DebtRelation> // DailyResponse에 추가
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

    // Repository에서 멤버별 정산 요약 데이터를 받아오기 위한 DTO
    data class SettlementSummary(
        val tripMemberId: Long,
        val totalPaid: BigDecimal,
        val totalAssigned: BigDecimal
    )
}