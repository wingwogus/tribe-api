package com.tribe.tribe_api.common.util.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails

object SecurityUtil {

    fun getCurrentMemberId(): Long {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw RuntimeException("Security Context에 인증 정보가 없습니다.")

        val principal = authentication.principal
        if (principal is UserDetails) {
            // UserDetails의 구현체인 CustomUserDetails에서 member ID를 가져옵니다.
            return (principal as CustomUserDetails).member.id
                ?: throw RuntimeException("인증 정보에서 사용자 ID를 찾을 수 없습니다.")
        }

        // 다른 타입의 Principal인 경우 (예: String) 처리
        throw RuntimeException("유효하지 않은 인증 주체입니다.")
    }
}