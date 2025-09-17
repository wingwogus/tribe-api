package com.tribe.tribe_api.common.util.oauth2

import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import java.util.UUID

data class OAuthAttributes(
    val attributes: Map<String, Any>,
    val nameAttributeKey: String,
    val name: String,
    val email: String,
    val picture: String?,
    val provider: Provider
) {
    companion object {
        fun of(registrationId: String, userNameAttributeName: String, attributes: Map<String, Any>): OAuthAttributes {
            return when (registrationId) {
                "google" -> ofGoogle(userNameAttributeName, attributes)
                "kakao" -> ofKakao(userNameAttributeName, attributes)
                else -> throw IllegalArgumentException("지원하지 않는 소셜 로그인입니다: $registrationId")
            }
        }

        private fun ofGoogle(userNameAttributeName: String, attributes: Map<String, Any>): OAuthAttributes {
            return OAuthAttributes(
                name = attributes["name"] as String,
                email = attributes["email"] as String,
                picture = attributes["picture"] as String?,
                attributes = attributes,
                nameAttributeKey = userNameAttributeName,
                provider = Provider.GOOGLE
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun ofKakao(userNameAttributeName: String, attributes: Map<String, Any>): OAuthAttributes {
            val kakaoAccount = attributes["kakao_account"] as? Map<String, Any>
                ?: throw IllegalArgumentException("카카오 계정 정보가 없습니다.")
            val profile = kakaoAccount["profile"] as? Map<String, Any>
                ?: throw IllegalArgumentException("카카오 프로필 정보가 없습니다.")

            return OAuthAttributes(
                name = profile["nickname"] as String,
                email = kakaoAccount["email"] as String,
                picture = profile["profile_image_url"] as String?,
                attributes = attributes,
                nameAttributeKey = userNameAttributeName,
                provider = Provider.KAKAO
            )
        }
    }

    fun toEntity(providerId: String): Member {
        return Member(
            nickname = this.name,
            email = this.email,
            password = UUID.randomUUID().toString(), // 비밀번호는 랜덤 생성
            avatar = this.picture,
            role = Role.USER,
            provider = this.provider,
            providerId = providerId,
            isFirstLogin = true
        )
    }
}