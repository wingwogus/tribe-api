package com.tribe.tribe_api.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class AppConfig {

    @Bean
    fun restTemplate(): RestTemplate {
        return RestTemplate()
    }

    @Bean
    fun webClient(builder: WebClient.Builder): WebClient {
        // 1. 메모리 사이즈를 10MB로 늘린 전략 설정
        val strategies = ExchangeStrategies.builder()
            .codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
            .build()

        // 2. 설정한 전략을 builder에 적용하여 build
        return builder
            .exchangeStrategies(strategies)
            .build()
    }
}