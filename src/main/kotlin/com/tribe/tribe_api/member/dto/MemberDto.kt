package com.tribe.tribe_api.member.dto

import com.tribe.tribe_api.member.entity.Member
import jakarta.validation.constraints.NotBlank

object MemberDto {
    data class UpdateNicknameRequest(
        @field:NotBlank(message = "닉네임은 비워둘 수 없습니다.")
        val nickname: String
    )

    data class Response(
        val memberId: Long,
        val nickname: String,
        val email: String,
        val avatar: String?,
        val isNewUser: Boolean
    ) {
        companion object {
            // from 팩토리 메소드를 companion object로 이동
            fun from(member: Member): Response {
                return Response(
                    memberId = member.id!!,
                    nickname = member.nickname,
                    email = member.email,
                    avatar = member.avatar,
                    isNewUser = member.isFirstLogin
                )
            }
        }
    }
}