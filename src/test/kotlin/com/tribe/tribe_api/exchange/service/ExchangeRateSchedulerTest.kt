package com.tribe.tribe_api.exchange.service

import com.ninjasquad.springmockk.MockkBean
import com.tribe.tribe_api.common.util.DateUtils
import com.tribe.tribe_api.exchange.client.ExchangeRateClient
import com.tribe.tribe_api.exchange.dto.ExchangeRateDto
import com.tribe.tribe_api.exchange.entity.Currency
import com.tribe.tribe_api.exchange.repository.CurrencyRepository
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class ExchangeRateSchedulerTest @Autowired constructor(
    private val exchangeRateScheduler: ExchangeRateScheduler,
    private val currencyRepository: CurrencyRepository
) {
    @MockkBean
    private lateinit var exchangeRateClient: ExchangeRateClient

    // Mock API 응답 데이터 설정
    private val mockApiSuccessResponse = listOf(
        ExchangeRateDto(1, "USD", "미국 달러", "1,350.50"), // USD
        ExchangeRateDto(1, "JPY(100)", "일본 옌", "925.33"),  // JPY(100) -> 9.2533으로 변환되어야 함
        ExchangeRateDto(1, "CNY", "중국 위안", "185.00") // 무시될 통화
    )

    @Test
    @DisplayName("스케줄러 실행 시 환율 정보가 정확히 DB에 저장되는지 검증")
    fun updateCurrency_Success_And_SavesCorrectly() {
        // given
        // Client 호출 시 Mock 응답을 반환하도록 설정
        every { exchangeRateClient.findExchange(any(), any()) } returns mockApiSuccessResponse

        // DateUtils.getTodayForApi()는 실제 코드가 사용되지만, Mocking된 Client가 응답을 반환하므로 테스트 가능
        val expectedApiDate = DateUtils.parseApiDate(DateUtils.getTodayForApi())

        // when
        exchangeRateScheduler.updateCurrency()

        // then
        // 1. DB에 저장된 통화 개수 확인 (USD, JPY 2개)
        assertThat(currencyRepository.findAll()).hasSize(2)

        // 2. USD 검증 (1,350.50)
        val usd = currencyRepository.findById("USD").get()
        assertThat(usd.curUnit).isEqualTo("USD")
        assertThat(usd.exchangeRate).isEqualByComparingTo(BigDecimal("1350.5000")) // scale=4로 저장됨
        assertThat(usd.date).isEqualTo(expectedApiDate)

        // 3. JPY 검증 (925.33 / 100 = 9.2533)
        val jpy = currencyRepository.findById("JPY").get()
        assertThat(jpy.curUnit).isEqualTo("JPY")
        assertThat(jpy.exchangeRate).isEqualByComparingTo(BigDecimal("9.2533"))
        assertThat(jpy.date).isEqualTo(expectedApiDate)
    }

    @Test
    @DisplayName("JPY(100) 통화 코드가 JPY로 정확히 변환되는지 검증")
    fun processExchange_JPYConversion_IsCorrect() {
        // given
        val jpyDto = ExchangeRateDto(1, "JPY(100)", "일본 옌", "931.09")

        // when: 스케줄러를 통해 JPY만 저장
        every { exchangeRateClient.findExchange(any(), any()) } returns listOf(jpyDto)
        exchangeRateScheduler.updateCurrency()

        // then
        val savedCurrency = currencyRepository.findByCurUnit("JPY")
        assertThat(savedCurrency).isNotNull()
        assertThat(savedCurrency!!.curUnit).isEqualTo("JPY")
        assertThat(savedCurrency.exchangeRate).isEqualByComparingTo(BigDecimal("9.3109"))
        assertThat(currencyRepository.findAll()).hasSize(1)
    }
}