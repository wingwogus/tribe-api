package com.tribe.tribe_api.member.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.member.dto.MemberDto
import com.tribe.tribe_api.member.service.MemberService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberService: MemberService
) {

    @GetMapping("/me")
    fun getMyInfo(): ResponseEntity<ApiResponse<MemberDto.Response>> {
        val memberInfo = memberService.getMyInfo()
        return ResponseEntity.ok(ApiResponse.success("내 정보 조회 성공", memberInfo))
    }

    @PatchMapping("/me")
    fun updateMyInfo(@Valid @RequestBody request: MemberDto.UpdateNicknameRequest): ResponseEntity<ApiResponse<MemberDto.Response>> {
        val memberInfo = memberService.updateNickname(request)
        return ResponseEntity.ok(ApiResponse.success("내 정보 수정 성공", memberInfo))
    }

    @GetMapping("/{memberId}")
    fun getMember(
        @PathVariable memberId: Long
    ): ResponseEntity<ApiResponse<MemberDto.Response>> {
        val memberInfo = memberService.getMemberInfo(memberId)
        return ResponseEntity.ok(ApiResponse.success("정보 조회 성공", memberInfo))
    }
}