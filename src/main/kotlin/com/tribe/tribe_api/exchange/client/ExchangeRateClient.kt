package com.tribe.tribe_api.exchange.client

import com.tribe.tribe_api.exchange.dto.ExchangeRateDto
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "exchangeRateApi",
    // [수정됨] application.yml에서 호스트만 가져옵니다.
    url = "\${exchange.rate.api-url}"
)
interface ExchangeRateClient {

    @GetMapping("/site/program/financial/exchangeJSON")
    fun findExchange(
        @RequestParam("authkey") authKey: String,
        @RequestParam("searchdate") searchDate: String,
        @RequestParam("data") data: String = "AP01"
    ): List<ExchangeRateDto>
}