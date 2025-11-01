package com.tribe.tribe_api.exchange.service

import com.tribe.tribe_api.common.util.DateUtils
import com.tribe.tribe_api.exchange.client.ExchangeRateClient
import com.tribe.tribe_api.exchange.dto.ExchangeRateDto
import com.tribe.tribe_api.exchange.entity.Currency
import com.tribe.tribe_api.exchange.repository.CurrencyRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class ExchangeRateScheduler(
    private val exchangeRateClient: ExchangeRateClient,
    private val currencyRepository: CurrencyRepository,
    @Value("\${key.exchange-rate.key}")
    private val authKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 월 ~ 금 14시 00분 부터 5분 주기로 55분까지 (12회) 진행합니다.
     * 한국수출입은행의 업데이트 시점에 맞춰 호출합니다.
     */
    @Scheduled(cron = "0 0/5 14 * * MON-FRI", zone = "Asia/Seoul") // 크론식 14시 시작으로 수정
    @Transactional
    fun updateCurrency() {
        try {
            // 주말일 경우 금요일 날짜로 요청합니다.
            val todayString = DateUtils.getTodayForApi()
            val apiDate = DateUtils.parseApiDate(todayString)

            // 1. 환율 조회 API 호출
            val exchanges = exchangeRateClient.findExchange(authKey, todayString)

            // 2. 조회된 값 중 USD, JPY만 필터링 및 BigDecimal로 변환
            val currenciesToSave = exchanges.mapNotNull { dto ->
                processExchange(dto, apiDate)
            }

            // 3. DB에 저장/업데이트 (PK가 같으면 UPDATE)
            currencyRepository.saveAll(currenciesToSave)

            log.info("Successfully saved/updated {} exchange rates for {}", currenciesToSave.size, apiDate)
        } catch (e: Exception) {
            log.error("Failed to update exchange rate: {}", e.message, e)
        }
    }

    /**
     * 특정 통화(USD, JPY)만 필터링하고, JPY(100)을 JPY(1)로 변환하여 Currency 엔티티를 생성합니다.
     * 모든 금액 처리는 BigDecimal로 수행하여 정밀도 오류를 방지합니다.
     */
    private fun processExchange(dto: ExchangeRateDto, date: LocalDate): Currency? {
        val targetCurrency: String
        val targetName: String

        when (dto.curUnit) {
            "JPY(100)" -> { targetCurrency = "JPY"; targetName = "일본 엔" }
            "USD" -> { targetCurrency = "USD"; targetName = "미국 달러" }
            else -> return null // 목표 통화가 아니면 무시
        }

        // 쉼표(,) 제거 후 BigDecimal로 파싱
        var exchangeRate = try {
            // 매매 기준율(deal_bas_r) 사용
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