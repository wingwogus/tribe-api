package com.tribe.tribe_api.exchange.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.exchange.client.ExchangeRateClient
import com.tribe.tribe_api.exchange.entity.Currency
import com.tribe.tribe_api.exchange.repository.CurrencyRepository
import com.tribe.tribe_api.exchange.util.ExchangeRateProcessor
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
            ExchangeRateProcessor.process(dto, date) // 유틸리티 호출
        }

        if (currenciesToSave.isEmpty()) return null

        // 3. DB에 저장/업데이트
        currencyRepository.saveAll(currenciesToSave)
        log.info("On-demand saved/updated {} exchange rates for {}", currenciesToSave.size, date)

        // 4. API 호출 결과에서 첫 번째(대표) 환율 객체를 반환합니다.
        return currenciesToSave.firstOrNull()
    }

}