package com.tribe.tribe_api.member.repository

import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {

    fun findByNickname(nickname: String): Member?

    fun findByEmail(email: String): Member?

    fun findByProviderAndProviderId(provider: Provider, providerId: String): Member?

    fun existsByNickname(nickname: String): Boolean

    fun existsByEmail(email: String): Boolean
}