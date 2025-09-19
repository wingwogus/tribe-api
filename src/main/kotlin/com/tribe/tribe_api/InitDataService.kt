package com.tribe.tribe_api

import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import com.tribe.tribe_api.trip.entity.Country
import com.tribe.tribe_api.trip.entity.Trip
import com.tribe.tribe_api.trip.entity.TripMember
import com.tribe.tribe_api.trip.entity.TripRole
import com.tribe.tribe_api.trip.repository.TripRepository
import jakarta.annotation.PostConstruct
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.LocalDate


@Component
class InitDataService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tripRepository: TripRepository
) {
    @PostConstruct
    fun dbInit() {
        if (memberRepository.findByEmail("user1@example.com") != null) {
            return
        }

        // 1. 테스트용 회원 2명 생성
        val memberA = Member(
            email = "user1@example.com",
            password = passwordEncoder.encode("password"),
            nickname = "테스터A",
            provider = Provider.LOCAL,
            role = Role.USER,
            isFirstLogin = false
        ).apply { memberRepository.save(this) }

        val memberB = Member(
            email = "user2@example.com",
            password = passwordEncoder.encode("password"),
            nickname = "테스터B",
            provider = Provider.LOCAL,
            role = Role.USER,
            isFirstLogin = false
        ).apply { memberRepository.save(this) }

        // 2. 테스트용 여행 생성
        Trip(
            title = "일본 오사카 미식 여행",
            startDate = LocalDate.of(2025, 10, 26),
            endDate = LocalDate.of(2025, 10, 30),
            country = Country.JAPAN
        ).apply {
            addMember(memberA, TripRole.OWNER)
            addMember(memberB, TripRole.MEMBER)
        }.also {
            tripRepository.save(it)
        }
    }
}

