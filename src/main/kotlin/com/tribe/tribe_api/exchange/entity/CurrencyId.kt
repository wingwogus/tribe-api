package com.tribe.tribe_api.exchange.entity

import java.io.Serializable
import java.time.LocalDate

class CurrencyId(
    var curUnit: String = "",
    var date: LocalDate = LocalDate.MIN
) : Serializable // 복합 키는 반드시 Serializable을 구현해야 합니다.