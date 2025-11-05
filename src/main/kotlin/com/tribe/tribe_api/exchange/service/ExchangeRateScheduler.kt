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
    @Value("\${exchange.rate.key}")
    private val authKey: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 월 ~ 금 14시 00분 부터 5분 주기로 55분까지 (12회) 진행합니다.
     * 한국수출입은행의 업데이트 시점에 맞춰 호출합니다.
     */
    @Scheduled(cron = "0 0/5 14 * * MON-FRI", zone = "Asia/Seoul") // 크론식 14시 시작으로 수정
//    @Scheduled(cron = "0 * * * * MON-FRI", zone = "Asia/Seoul") // 크론식 14시 시작했을때 못받았을때 이거 쓰면됨. 이걸로 하면은 아무때나해도 db에 들어가짐
    @Transactional
    fun updateCurrency() {
        try {
            // 주말일 경우 금요일 날짜로 요청합니다.
            val todayString = DateUtils.getTodayForApi()
            val apiDate = DateUtils.parseApiDate(todayString)

            // 1. 환율 조회 API 호출
            val exchanges = exchangeRateClient.findExchange(authKey, todayString)

            // 2. 필터링 로직 없이 모든 통화를 처리하도록 수정
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
     * 통화 필터링 로직을 제거하고, 100단위 통화만 1단위로 변환하여 Currency 엔티티를 생성합니다.
     */
    private fun processExchange(dto: ExchangeRateDto, date: LocalDate): Currency? {
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