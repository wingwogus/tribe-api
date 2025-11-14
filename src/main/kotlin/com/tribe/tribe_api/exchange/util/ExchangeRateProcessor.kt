package com.tribe.tribe_api.exchange.util

import com.tribe.tribe_api.exchange.dto.ExchangeRateDto
import com.tribe.tribe_api.exchange.entity.Currency
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// API 응답 DTO를 DB 엔티티로 변환하고 JPY(100단위)를 1단위로 변환하는 핵심 로직이 이곳에 모임.
object ExchangeRateProcessor {
    private val log = LoggerFactory.getLogger(ExchangeRateProcessor::class.java)

    /**
     * 통화 필터링 로직을 제거하고, 100단위 통화만 1단위로 변환하여 Currency 엔티티를 생성합니다.
     */
    fun process(dto: ExchangeRateDto, date: LocalDate): Currency? {
        val rawCurrencyUnit = dto.curUnit

        // 1. 통화 코드 결정 및 100단위 처리 여부 확인
        val is100Unit = rawCurrencyUnit.endsWith("(100)")
        val targetCurrency: String = if (is100Unit) {
            rawCurrencyUnit.substringBefore('(') // JPY(100) -> JPY
        } else if (rawCurrencyUnit.contains('(') || rawCurrencyUnit.contains(')')) {
            return null // 알 수 없는 괄호 형식은 무시
        } else {
            rawCurrencyUnit // USD, EUR 등 일반 통화
        }

        // 한국 원화(KRW)는 KRW/KRW이므로 저장할 필요 없습니다.
        if (targetCurrency == "KRW") {
            return null
        }

        // 2. 쉼표(,) 제거 후 BigDecimal로 파싱
        var exchangeRate = try {
            // 매매 기준율(deal_bas_r) 사용
            BigDecimal(dto.dealBasR.replace(",", ""))
        } catch (e: NumberFormatException) {
            log.warn("Failed to parse exchange rate for {}: {}", dto.curUnit, dto.dealBasR)
            return null
        }

        // 3. 100단위로 받은 경우 1단위로 변환 (JPY(100) 등)
        if (is100Unit) {
            // Currency 엔티티의 scale=4에 맞춰 정밀하게 나누기
            exchangeRate = exchangeRate.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP)
        }

        // 데이터베이스 scale(4)에 맞춰 최종 스케일 조정 (변환이 없더라도 4자리 보장)
        exchangeRate = exchangeRate.setScale(4, RoundingMode.HALF_UP)

        return Currency(
            curUnit = targetCurrency,
            curName = dto.curName,
            exchangeRate = exchangeRate,
            date = date
        )
    }
}