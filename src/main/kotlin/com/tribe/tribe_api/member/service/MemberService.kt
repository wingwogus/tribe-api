package com.tribe.tribe_api.member.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.member.dto.MemberDto
import com.tribe.tribe_api.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class MemberService(
    private val memberRepository: MemberRepository
) {
    val logger = LoggerFactory.getLogger(javaClass)

    fun getMyInfo(): MemberDto.Response {
        val currentMemberId = SecurityUtil.getCurrentMemberId()

        return getMemberInfo(currentMemberId)
    }

    fun getMemberInfo(memberId: Long): MemberDto.Response {
        val member = memberRepository.findById(memberId)
            .orElseThrow({ BusinessException(ErrorCode.MEMBER_NOT_FOUND) })

        val responseDto = MemberDto.Response.from(member)

        if (member.isFirstLogin) {
            member.isFirstLogin = false
        }

        return responseDto
    }

    fun updateNickname(request: MemberDto.UpdateNicknameRequest): MemberDto.Response {
        val currentMemberId = SecurityUtil.getCurrentMemberId()

        val member = memberRepository.findById(currentMemberId)
            .orElseThrow({ BusinessException(ErrorCode.MEMBER_NOT_FOUND) })

        member.nickname = request.nickname

        val responseDto = MemberDto.Response.from(member)

        logger.info("Nickname updated. Member Id {}", member.id)

        return responseDto
    }
}