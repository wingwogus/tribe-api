package com.tribe.tribe_api.expense.dto

import java.math.BigDecimal

data class DebtRelation(
    val fromMemberName:String,
    val fromMemberId:Long,
    val toMemberName:String,
    val toMemberId:Long,
    val amount: BigDecimal
)