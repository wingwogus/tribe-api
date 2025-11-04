package com.tribe.tribe_api.exchange.entity

import java.io.Serializable
import java.time.LocalDate

class CurrencyId(
    var curUnit: String = "",
    var date: LocalDate = LocalDate.MIN
) : Serializable { // 복합 키는 반드시 Serializable을 구현해야 합니다.

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CurrencyId) return false
        return curUnit == other.curUnit && date == other.date
    }

    override fun hashCode(): Int =
        31 * curUnit.hashCode() + date.hashCode()
}