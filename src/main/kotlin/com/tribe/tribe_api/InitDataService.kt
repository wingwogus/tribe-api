package com.tribe.tribe_api

import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import jakarta.annotation.PostConstruct
import jakarta.transaction.Transactional
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component


@Component
class InitDataService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder
) {
    @PostConstruct
    fun dbInit() {
        if (memberRepository.findByEmail("user1@example.com") != null) {
            return
        }

        // 1. 테스트용 회원 2명 생성
        Member(
            email = "user1@example.com",
            password = passwordEncoder.encode("password"),
            nickname = "테스터A",
            provider = Provider.LOCAL,
            role = Role.USER,
            isFirstLogin = false
        ).apply { memberRepository.save(this) }

        Member(
            email = "user2@example.com",
            password = passwordEncoder.encode("password"),
            nickname = "테스터B",
            provider = Provider.LOCAL,
            role = Role.USER,
            isFirstLogin = false
        ).apply { memberRepository.save(this) }
    }
}

