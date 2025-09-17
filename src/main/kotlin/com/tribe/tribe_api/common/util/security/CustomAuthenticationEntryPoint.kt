package com.tribe.tribe_api.common.util.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        setErrorResponse(response, ErrorCode.UNAUTHORIZED_ACCESS)
    }

    private fun setErrorResponse(response: HttpServletResponse, errorCode: ErrorCode) {
        response.contentType = "application/json;charset=UTF-8"
        response.status = errorCode.status.value()

        val apiResponse = ApiResponse.error<Unit>(errorCode.message)

        response.writer.write(objectMapper.writeValueAsString(apiResponse))
    }
}