// wingwogus/tribe-api/tribe-api-dailyExpense/src/main/kotlin/com/tribe/tribe_api/exchange/service/ExchangeRateService.kt

package com.tribe.tribe_api.exchange.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.exchange.client.ExchangeRateClient
import com.tribe.tribe_api.exchange.dto.ExchangeRateDto
import com.tribe.tribe_api.exchange.entity.Currency
import com.tribe.tribe_api.exchange.repository.CurrencyRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class ExchangeRateService(
    private val exchangeRateClient: ExchangeRateClient,
    private val currencyRepository: CurrencyRepository,
    @Value("\${exchange.rate.key}") private val authKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 특정 날짜의 환율을 API에서 조회하고, DB에 저장합니다.
     * 이 메서드는 외부 트랜잭션과 독립적으로 실행됩니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun fetchAndSaveExchangeRate(date: LocalDate): Currency? {
        val dateString = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))

        // 1. 환율 조회 API 호출
        val exchanges = try {
            val response = exchangeRateClient.findExchange(authKey, dateString)
            log.info("API Response received for date {}: {} items", date, response.size)
            response
        } catch (e: Exception) {
            log.error("Historical API call failed for date {}: {}", date, e.message)
            return null
        }

        if (exchanges.isEmpty()) {
            log.warn("API returned empty list for date {}. Cannot save.", date)
            return null
        }

        // 2. 필터링 로직 없이 모든 통화를 처리하도록 수정
        val currenciesToSave = exchanges.mapNotNull { dto ->
            processExchangeDto(dto, date)
        }

        if (currenciesToSave.isEmpty()) return null

        // 3. DB에 저장/업데이트
        currencyRepository.saveAll(currenciesToSave)
        log.info("On-demand saved/updated {} exchange rates for {}", currenciesToSave.size, date)

        // 4. API 호출 결과에서 첫 번째(대표) 환율 객체를 반환합니다.
        return currenciesToSave.firstOrNull()
    }

    /**
     * 통화 필터링 로직을 제거하고, 100단위 통화만 1단위로 변환하여 Currency 엔티티를 생성합니다.
     */
    private fun processExchangeDto(dto: ExchangeRateDto, date: LocalDate): Currency? {
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
            BigDecimal(dto.dealBasR.replace(",", ""))
        } catch (e: NumberFormatException) {
            log.warn("Failed to parse exchange rate for {}: {}", dto.curUnit, dto.dealBasR)
            return null
        }

        // 3. 100단위로 받은 경우 1단위로 변환 (JPY(100) 등)
        if (is100Unit) {
            exchangeRate = exchangeRate.divide(BigDecimal("100"))
        }

        return Currency(
            curUnit = targetCurrency,
            curName = dto.curName,
            exchangeRate = exchangeRate,
            date = date
        )
    }
}