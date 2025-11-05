package com.tribe.tribe_api.exchange.service

import com.tribe.tribe_api.common.util.DateUtils
import com.tribe.tribe_api.exchange.client.ExchangeRateClient
// import com.tribe.tribe_api.exchange.dto.ExchangeRateDto // 더 이상 필요 없음
// import com.tribe.tribe_api.exchange.entity.Currency // 더 이상 필요 없음
import com.tribe.tribe_api.exchange.repository.CurrencyRepository
import com.tribe.tribe_api.exchange.util.ExchangeRateProcessor // ✅ 추가
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
                ExchangeRateProcessor.process(dto, apiDate) // ✅ 변경: 유틸리티 호출
            }

            // 3. DB에 저장/업데이트 (PK가 같으면 UPDATE)
            currencyRepository.saveAll(currenciesToSave)

            log.info("Successfully saved/updated {} exchange rates for {}", currenciesToSave.size, apiDate)
        } catch (e: Exception) {
            log.error("Failed to update exchange rate: {}", e.message, e)
        }
    }
}