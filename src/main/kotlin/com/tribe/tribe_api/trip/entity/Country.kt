package com.tribe.tribe_api.trip.entity

import lombok.AllArgsConstructor
import lombok.Getter

@AllArgsConstructor
@Getter
enum class Country(
    val code: String,
    val koreanName: String,
) {
    JAPAN("JP", "일본"),
    SOUTH_KOREA("KR", "대한민국"),
    UNITED_STATES("US", "미국");
}