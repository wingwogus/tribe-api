package com.tribe.tribe_api.common.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId // [추가]
import java.time.format.DateTimeFormatter

object DateUtils {
    // API 호출 기준 타임존을 'Asia/Seoul'로 명시적으로 정의
    private val apiZone: ZoneId = ZoneId.of("Asia/Seoul")
    private val apiFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun getTodayForApi(): String {
        // [핵심 수정]: 서버 환경과 무관하게 한국 시간 기준으로 현재 날짜를 가져옵니다.
        var currentDate = LocalDate.now(apiZone)


        // 토요일일 경우 금요일로 변경
        if (currentDate.dayOfWeek == DayOfWeek.SATURDAY) {
            currentDate = currentDate.minusDays(1)
        }
        // 일요일일 경우 금요일로 변경
        if (currentDate.dayOfWeek == DayOfWeek.SUNDAY) {
            currentDate = currentDate.minusDays(2)
        }
        return currentDate.format(apiFormatter) // 상수화된 포맷터 사용
    }

    // "yyyyMMdd" 문자열을 LocalDate로 변환
    fun parseApiDate(dateString: String): LocalDate {
        // val formatter = DateTimeFormatter.ofPattern("yyyyMMdd") // 제거
        return LocalDate.parse(dateString, apiFormatter) // 상수화된 포맷터 사용
    }
}