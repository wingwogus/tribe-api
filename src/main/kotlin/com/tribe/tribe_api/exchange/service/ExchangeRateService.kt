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
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class ExchangeRateService(
    private val exchangeRateClient: ExchangeRateClient,
    private val currencyRepository: CurrencyRepository,
    @Value("\${key.exchange-rate.key}") private val authKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 특정 날짜의 환율을 API에서 조회하고, DB에 저장합니다.
     * 이 메서드는 외부 트랜잭션과 독립적으로 실행됩니다.
     */
    @Transactional
    fun fetchAndSaveExchangeRate(date: LocalDate): Currency? {
        val dateString = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))

        // 1. 환율 조회 API 호출
        val exchanges = try {
            val response = exchangeRateClient.findExchange(authKey, dateString)
            log.info("API Response received for date {}: {} items", date, response.size)
            response
        } catch (e: Exception) {
            // Server redirected too many times 오류가 여기서 로그에 남습니다.
            log.error("Historical API call failed for date {}: {}", date, e.message)
            return null
        }

        if (exchanges.isEmpty()) {
            log.warn("API returned empty list for date {}. Cannot save.", date)
            return null
        }

        // 2. 조회된 값 중 USD, JPY만 필터링 및 BigDecimal로 변환하여 DB에 저장
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

    // ExchangeRateScheduler.kt의 processExchange 로직을 그대로 사용
    private fun processExchangeDto(dto: ExchangeRateDto, date: LocalDate): Currency? {
        val targetCurrency: String
        val targetName: String

        when (dto.curUnit) {
            "JPY(100)" -> { targetCurrency = "JPY"; targetName = "일본 엔" }
            "USD" -> { targetCurrency = "USD"; targetName = "미국 달러" }
            else -> return null // 목표 통화가 아니면 무시
        }

        // 쉼표(,) 제거 후 BigDecimal로 파싱
        var exchangeRate = try {
            BigDecimal(dto.dealBasR.replace(",", ""))
        } catch (e: NumberFormatException) {
            log.warn("Failed to parse exchange rate for {}: {}", dto.curUnit, dto.dealBasR)
            return null
        }

        // JPY는 100단위로 받으므로 1단위로 변환
        if (dto.curUnit == "JPY(100)") {
            exchangeRate = exchangeRate.divide(BigDecimal("100"))
        }

        return Currency(
            curUnit = targetCurrency,
            curName = targetName,
            exchangeRate = exchangeRate,
            date = date
        )
    }
}