package com.tribe.tribe_api.common.util.jwt

import com.fasterxml.jackson.databind.ObjectMapper
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.ApiResponse
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtExceptionFilter(
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            filterChain.doFilter(request, response)
        } catch (e: BusinessException) {
            // JwtAuthenticationFilter 에서 발생한 BusinessException을 잡아서 처리
            setErrorResponse(response, e.errorCode)
        }
    }

    private fun setErrorResponse(response: HttpServletResponse, errorCode: ErrorCode) {
        response.contentType = "application/json;charset=UTF-8"
        response.status = errorCode.status.value()

        val apiResponse = ApiResponse.error<Unit>(errorCode.message)
        response.writer.write(objectMapper.writeValueAsString(apiResponse))
    }
}