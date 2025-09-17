package com.tribe.tribe_api.common.util

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?
) {
    companion object {
        // 성공 응답 (데이터 포함)
        fun <T> success(data: T): ApiResponse<T> {
            return ApiResponse(true, "OK", data)
        }

        // 성공 응답 (메시지 + 데이터)
        fun <T> success(message: String, data: T?): ApiResponse<T> {
            return ApiResponse(true, message, data)
        }

        // 실패 응답
        fun <T> error(message: String): ApiResponse<T> {
            return ApiResponse(false, message, null)
        }
    }
}