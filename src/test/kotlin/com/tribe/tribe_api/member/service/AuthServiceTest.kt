package com.tribe.tribe_api.member.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.service.RedisService
import com.tribe.tribe_api.member.dto.AuthDto
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@SpringBootTest
@Transactional
class AuthServiceIntegrationTest @Autowired constructor(
    private val authService: AuthService,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val redisService: RedisService
) {

    companion object {
        private const val AUTH_CODE_PREFIX = "AuthCode "
        private const val VERIFIED_EMAIL_PREFIX = "VerifiedEmail "
    }

    @AfterEach
    fun tearDown() {
        redisService.deleteValues("$AUTH_CODE_PREFIX" + "test@tribe.com")
        redisService.deleteValues("$VERIFIED_EMAIL_PREFIX" + "test@tribe.com")
    }

    @Test
    @DisplayName("회원가입 성공")
    fun signUp_Success_WhenEmailIsVerified() {
        // given
        val request = AuthDto.SignUpRequest("test@tribe.com", "password123", "testUser")
        redisService.setValues("$VERIFIED_EMAIL_PREFIX${request.email}", "true", Duration.ofMinutes(10))

        // when
        authService.signUp(request)

        // then
        val foundMember = memberRepository.findByEmail(request.email)
            ?: throw AssertionError("Member was not saved.")

        assertThat(foundMember.nickname).isEqualTo(request.nickname)
        assertThat(passwordEncoder.matches(request.password, foundMember.password)).isTrue
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 미 인증시")
    fun signUp_Fail_WhenEmailIsNotVerified() {
        // given
        val request = AuthDto.SignUpRequest("test@tribe.com", "password123", "testUser")

        // when & then
        val exception = assertThrows<BusinessException> {
            authService.signUp(request)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED)
    }

    @Test
    @DisplayName("닉네임 중복 체크 성공")
    fun checkDuplicatedNickname_Success_WhenNicknameIsAvailable() {
        // given
        val request = AuthDto.VerifiedNicknameRequest("availableNickname")

        // when & then
        authService.checkDuplicatedNickname(request) // Should not throw exception
    }

    @Test
    @DisplayName("닉네임 중복 체크 실패")
    fun checkDuplicatedNickname_Fail_WhenNicknameIsDuplicated() {
        // given
        memberRepository.save(Member(
            "test@tribe.com",
            "pw",
            "duplicatedNickname",
            null,
            Role.USER,
            Provider.LOCAL,
            isFirstLogin = false))
        val request = AuthDto.VerifiedNicknameRequest("duplicatedNickname")

        // when & then
        val exception = assertThrows<BusinessException> {
            authService.checkDuplicatedNickname(request)
        }
        assertThat(exception.errorCode).isEqualTo(ErrorCode.DUPLICATE_NICKNAME)
    }
}