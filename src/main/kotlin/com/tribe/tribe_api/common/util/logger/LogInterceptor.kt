package com.tribe.tribe_api.common.util.logger

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class LogInterceptor : HandlerInterceptor {

    val log: Logger = LoggerFactory.getLogger(javaClass)

    // 1. 컨트롤러 실행 전
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // 요청 시작 시간을 request에 저장
        request.setAttribute("startTime", System.currentTimeMillis())

        // MDC(traceId, userId)가 이미 찍히기 때문에 메소드, URI, Client 코드만 넘김
        log.info("[REQ START] {} {}, client={}", request.method, request.requestURI, getClientIP(request))
        return true
    }

    // 2. 모든 것(컨트롤러, 예외 처리, 뷰 렌더링)이 끝난 후
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        val startTime = request.getAttribute("startTime") as Long
        val duration = System.currentTimeMillis() - startTime
        val status = response.status
        val clientIP = getClientIP(request)

        // 예외가 발생했는지 여부 확인
        if (ex != null) {
            // 예외가 발생했다면 ERROR 레벨로 로그를 남김 (스택 트레이스 포함)
            log.error(
                "[REQ ERROR] {} {}, status={}, duration={}ms, client={}",
                request.method, request.requestURI, status, duration, clientIP, ex
            )
        } else {
            // 정상 종료
            log.info(
                "[REQ END] {} {}, status={}, duration={}ms, client={}",
                request.method, request.requestURI, status, duration, clientIP
            )
        }
    }

    /**
     * 클라이언트 IP 주소를 가져오는 헬퍼 함수 (리버스 프록시 환경)
     */
    private fun getClientIP(request: HttpServletRequest): String {
        // 1. X-Forwarded-For 헤더 확인
        var ip = request.getHeader("X-Forwarded-For")

        // 2. 다른 프록시 표준 헤더 확인
        if (ip == null || ip.isEmpty() || "unknown".equals(ip, ignoreCase = true)) {
            ip = request.getHeader("Proxy-Client-IP")
        }
        if (ip == null || ip.isEmpty() || "unknown".equals(ip, ignoreCase = true)) {
            ip = request.getHeader("WL-Proxy-Client-IP")
        }
        if (ip == null || ip.isEmpty() || "unknown".equals(ip, ignoreCase = true)) {
            ip = request.getHeader("HTTP_CLIENT_IP")
        }
        if (ip == null || ip.isEmpty() || "unknown".equals(ip, ignoreCase = true)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR")
        }

        // 3. 모든 헤더에 값이 없다면 remoteAddr 사용
        if (ip == null || ip.isEmpty() || "unknown".equals(ip, ignoreCase = true)) {
            ip = request.remoteAddr
        }

        // 4. X-Forwarded-For는 "client, proxy1, proxy2"처럼 여러 IP를 포함할 수 있으므로,
        //    가장 첫 번째 IP(실제 클라이언트 IP)만 잘라서 반환
        return ip.split(",")[0].trim()
    }
}