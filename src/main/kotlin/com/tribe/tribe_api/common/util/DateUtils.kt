package com.tribe.tribe_api.common.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DateUtils {
    // API 호출을 위한 "yyyyMMdd" 형태의 문자열을 반환합니다.
    fun getTodayForApi(): String {
        var currentDate = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")

        // 토요일일 경우 금요일로 변경
        if (currentDate.dayOfWeek == DayOfWeek.SATURDAY) {
            currentDate = currentDate.minusDays(1)
        }
        // 일요일일 경우 금요일로 변경
        if (currentDate.dayOfWeek == DayOfWeek.SUNDAY) {
            currentDate = currentDate.minusDays(2)
        }

        return currentDate.format(formatter)
    }

    // "yyyyMMdd" 문자열을 LocalDate로 변환
    fun parseApiDate(dateString: String): LocalDate {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        return LocalDate.parse(dateString, formatter)
    }
}