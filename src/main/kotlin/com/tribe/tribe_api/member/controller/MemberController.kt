package com.tribe.tribe_api.member.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.member.dto.MemberDto
import com.tribe.tribe_api.member.service.MemberService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberService: MemberService // 주 생성자를 통한 의존성 주입
) {

    @GetMapping("/me")
    fun getMyInfo(): ResponseEntity<ApiResponse<MemberDto.Response>> {
        val memberInfo = memberService.getMemberInfo()
        return ResponseEntity.ok(ApiResponse.success(memberInfo))
    }
}