package com.tribe.tribe_api.exchange.repository

import com.tribe.tribe_api.exchange.entity.Currency
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface CurrencyRepository : JpaRepository<Currency, String> {
    // 특정 통화 코드로 조회 (PK 조회)
    fun findByCurUnit(curUnit: String): Currency?

    // 지출일 기준 환율을 조회하기 위한 메서드 (정산 로직에서 사용 예정)
    fun findByCurUnitAndDate(curUnit: String, date: LocalDate): Currency?
}