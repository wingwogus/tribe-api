package com.tribe.tribe_api.itinerary.dto

object PageResponseDto {

    data class PageResponseDto<T>(
        val content: List<T>,       // 실제 데이터 목록
        val pageNumber: Int,        // 현재 페이지 번호 (0부터 시작)
        val pageSize: Int,          // 페이지 당 데이터 수
        val totalPages: Int,        // 전체 페이지 수
        val totalElements: Long,    // 전체 데이터 개수
        val isLast: Boolean         // 마지막 페이지 여부
    )
}