package com.tribe.tribe_api.exchange.client

import com.tribe.tribe_api.exchange.dto.ExchangeRateDto
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "exchangeRateApi",
    url = "\${exchange.rate.api-url}" // application.yml에서 URL을 읽어옵니다.
)
interface ExchangeRateClient {
    @GetMapping
    fun findExchange(
        @RequestParam("authkey") authKey: String,
        @RequestParam("searchdate") searchDate: String
    ): List<ExchangeRateDto>
}