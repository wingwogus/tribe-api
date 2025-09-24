package com.tribe.tribe_api.member.controller

import com.tribe.tribe_api.common.util.ApiResponse
import com.tribe.tribe_api.common.util.jwt.JwtToken
import com.tribe.tribe_api.member.dto.MemberDto
import com.tribe.tribe_api.member.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody loginRequestDto: MemberDto.LoginRequest): ResponseEntity<ApiResponse<JwtToken>> {
        val response = authService.login(loginRequestDto)
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", response))
    }

    @PostMapping("/reissue")
    fun reissue(@Valid @RequestBody request: MemberDto.ReissueRequest): ResponseEntity<ApiResponse<JwtToken>> {
        val newToken = authService.reissue(request)
        return ResponseEntity.ok(ApiResponse.success("RefreshToken 재발급 성공", newToken))
    }

    @PostMapping("/send-email")
    fun sendMessage(@Valid @RequestBody emailRequestDto: MemberDto.EmailRequest): ResponseEntity<ApiResponse<Unit>> {
        authService.sendCodeToEmail(emailRequestDto.email)
        return ResponseEntity.ok(ApiResponse.success("이메일 전송에 성공하였습니다", null))
    }

    @PostMapping("/verification")
    fun verification(@Valid @RequestBody verifiedRequestDto: MemberDto.VerifiedRequest): ResponseEntity<ApiResponse<Unit>> {
        authService.verifiedCode(verifiedRequestDto)
        return ResponseEntity.ok(ApiResponse.success("코드 인증에 성공하였습니다.", null))
    }

    @PostMapping("/verification-nickname")
    fun verificationNickname(@Valid @RequestBody verifiedRequestDto: MemberDto.VerifiedNicknameRequest): ResponseEntity<ApiResponse<Unit>> {
        authService.checkDuplicatedNickname(verifiedRequestDto)
        return ResponseEntity.ok(ApiResponse.success("사용 가능한 닉네임입니다!", null))
    }

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody signUpRequest: MemberDto.SignUpRequest): ResponseEntity<ApiResponse<Unit>> {
        authService.signUp(signUpRequest)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("회원가입에 성공하였습니다", null))
    }

    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal userDetails: UserDetails): ResponseEntity<ApiResponse<Unit>> {
        authService.logout(userDetails.username)
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공", null))
    }
}