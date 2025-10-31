package com.tribe.tribe_api.common.exception

import com.tribe.tribe_api.common.util.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    val logger = LoggerFactory.getLogger(javaClass)

    // Java의 BusinessException.class -> Kotlin의 BusinessException::class
    // 반환 타입을 명시적으로 선언합니다: ResponseEntity<ApiResponse<Unit>>
    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(ex: BusinessException): ResponseEntity<ApiResponse<Unit>> {
        val status = ex.errorCode.status
        val body = ApiResponse.error<Unit>(ex.errorCode.message)
        logger.error(ex.errorCode.message, body)
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: "잘못된 요청입니다."

        val body = ApiResponse.error<Unit>(message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<ApiResponse<Unit>> {
        val body = ApiResponse.error<Unit>("아이디와 비밀번호를 확인해주세요")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleAll(ex: Exception): ResponseEntity<ApiResponse<Unit>> {
        // ex.message가 null일 수 있으므로 안전 호출(?.) 사용
        val body = ApiResponse.error<Unit>("서버 오류가 발생했습니다: ${ex.message}")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
    }
}