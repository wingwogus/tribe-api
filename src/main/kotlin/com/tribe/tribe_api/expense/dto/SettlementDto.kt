package com.tribe.tribe_api.expense.dto

import com.tribe.tribe_api.trip.entity.TripMember
import java.math.BigDecimal
import java.time.LocalDate

object SettlementDto {

    data class DailyResponse(
        val date: LocalDate,
        val dailyTotalAmount: BigDecimal, // KRW 총액
        val expenses: List<DailyExpenseSummary>,
        val memberSummaries: List<MemberDailySummary>,
        val debtRelations: List<DebtRelation>
    )

    data class DailyExpenseSummary(
        val expenseId: Long,
        val title: String,
        val payerName: String,
        val totalAmount: BigDecimal,       // KRW 변환 금액
        val originalAmount: BigDecimal,    // 원화/외화 금액
        val currencyCode: String           // 원화/외화 코드 (JPY, USD, KRW)
    )

    data class MemberDailySummary(
        val memberId: Long,
        val memberName: String,
        val paidAmount: BigDecimal,       // KRW
        val assignedAmount: BigDecimal    // KRW
    )

    data class TotalResponse(
        val memberBalances: List<MemberBalance>,
        val debtRelations: List<DebtRelation>,
        val isExchangeRateApplied: Boolean = true // New: 환율 적용 여부 명시
    )

    // 변경내역있습니다. Hyeni
    data class MemberBalance(
        val tripMemberId: Long,
        val nickname: String,
        val balance: BigDecimal, // KRW
        // New: 멤버가 지출했거나 분담받은 외화 종류 목록 (간결한 정보 제공)
        val foreignCurrenciesUsed: List<String> = emptyList()
    )

    // Repository에서 멤버별 정산 요약 데이터를 받아오기 위한 DTO
    data class SettlementSummary(
        val tripMemberId: Long,
        val totalPaid: BigDecimal,
        val totalAssigned: BigDecimal
    )

    data class DebtRelation(
        val fromNickname: String,
        val fromTripMemberId: Long,
        val toNickname: String,
        val toTripMemberId: Long,
        val amount: BigDecimal // KRW
    )
    data class MemberSettlementData(
        val member: TripMember,
        val paidAmountKrw: BigDecimal,
        val assignedAmountKrw: BigDecimal,
        val foreignCurrencies: List<String>
    )
}