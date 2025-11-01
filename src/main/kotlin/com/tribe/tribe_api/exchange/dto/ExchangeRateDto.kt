package com.tribe.tribe_api.exchange.dto

import com.fasterxml.jackson.annotation.JsonProperty

// API 응답 List<ExchangeRateDto> 형태로 받음
data class ExchangeRateDto(
    val result: Int, // 조회 결과 코드
    @JsonProperty("cur_unit")
    val curUnit: String, // 통화코드 (예: USD, JPY(100))
    @JsonProperty("cur_nm")
    val curName: String, // 국가/통화명
    @JsonProperty("deal_bas_r")
    val dealBasR: String // 매매 기준율 (쉼표 포함된 문자열)
    // 필요한 다른 필드는 생략합니다.
)