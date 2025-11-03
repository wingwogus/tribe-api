package com.tribe.tribe_api.exchange.repository

import com.tribe.tribe_api.exchange.entity.Currency
import com.tribe.tribe_api.exchange.entity.CurrencyId // CurrencyId import 필요 (복합 키 사용 시)
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

// JpaRepository의 PK 타입을 CurrencyId로 변경했음을 가정합니다.
interface CurrencyRepository : JpaRepository<Currency, CurrencyId> {

    // 특정 날짜의 환율 조회 (일별 정산의 convertToKrw에서 사용)
    fun findByCurUnitAndDate(curUnit: String, date: LocalDate): Currency?

    // [추가됨]: 통화 단위로 가장 최신 날짜의 환율 하나만 가져옵니다. (전체 정산에 사용)
    fun findTopByCurUnitOrderByDateDesc(curUnit: String): Currency?
}