package com.tribe.tribe_api.common.util.logger

import com.tribe.tribe_api.common.util.security.CustomUserDetails
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

/**
 * 모든 HTTP 요청에 대해 MDC(Mapped Diagnostic Context)를 설정하는 필터
 *
 * 1. traceId: 요청마다 고유한 ID를 생성
 * 2. userId: 사용자 ID (로그인 안 했으면 GUEST)
 */
@Component
class MDCLoggingFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 1. 요청마다 고유한 traceId 생성 (간결하게 8자리 UUID 사용)
        val traceId = UUID.randomUUID().toString()
        MDC.put("traceId", traceId)

        // SecurityUtil 대신 직접 "방어적"으로 사용자 ID 조회
        try {
            val authentication = SecurityContextHolder.getContext().authentication

            val userId = if (authentication != null &&
                authentication.isAuthenticated &&
                authentication.principal is CustomUserDetails
            ) {
                // 인증된 사용자라면 memberId를 가져옴
                (authentication.principal as CustomUserDetails).member.id.toString()
            } else {
                // 그 외 모든 경우 (익명 사용자, 로그인 전 등)
                "GUEST"
            }
            MDC.put("userId", userId)

            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}