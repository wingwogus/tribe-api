package com.tribe.tribe_api.expense.dto

data class TotalSettlementResponse(
    val memberBalances: List<MemberBalance>,
    val debtRelations: List<DebtRelation>
)