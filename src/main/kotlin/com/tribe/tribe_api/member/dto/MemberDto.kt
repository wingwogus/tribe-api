package com.tribe.tribe_api.member.dto

import com.tribe.tribe_api.common.annotation.CustomEmail
import com.tribe.tribe_api.member.entity.Member
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

object MemberDto {
    data class EmailRequest(
        @field:CustomEmail val email: String
    )

    data class VerifiedRequest(
        @field:CustomEmail val email: String,
        @field:NotBlank(message = "인증번호를 입력해주세요")
        @field:Size(min = 6, max = 6)
        val code: String
    )

    data class VerifiedNicknameRequest(
        @field:NotBlank(message = "닉네임을 입력해주세요") val nickname: String
    )

    data class SignUpRequest(
        @field:CustomEmail val email: String,
        @field:NotBlank(message = "비밀번호를 입력해주세요") val password: String,
        @field:NotBlank(message = "닉네임을 입력해주세요") val nickname: String,
        val avatar: String? = null
    )

    data class LoginRequest(
        @field:CustomEmail val email: String,
        @field:NotBlank(message = "비밀번호를 입력해주세요") val password: String
    )

    data class ReissueRequest(
        @field:NotBlank(message = "AccessToken을 입력해주세요") val accessToken: String,
        @field:NotBlank(message = "RefreshToken을 입력해주세요") val refreshToken: String
    )

    data class Response(
        val nickname: String,
        val email: String,
        val avatar: String?,
        val isNewUser: Boolean
    ) {
        companion object {
            // from 팩토리 메소드를 companion object로 이동
            fun from(member: Member): Response {
                return Response(
                    nickname = member.nickname,
                    email = member.email,
                    avatar = member.avatar,
                    isNewUser = member.isFirstLogin
                )
            }
        }
    }
}