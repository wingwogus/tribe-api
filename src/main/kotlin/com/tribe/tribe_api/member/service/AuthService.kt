package com.tribe.tribe_api.member.service

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.jwt.JwtToken
import com.tribe.tribe_api.common.util.jwt.JwtTokenProvider
import com.tribe.tribe_api.common.util.service.MailService
import com.tribe.tribe_api.common.util.service.RedisService
import com.tribe.tribe_api.member.dto.MemberDto
import com.tribe.tribe_api.member.entity.Member
import com.tribe.tribe_api.member.entity.Provider
import com.tribe.tribe_api.member.entity.Role
import com.tribe.tribe_api.member.repository.MemberRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import kotlin.random.Random

@Service
@Transactional
class AuthService(
    private val jwtTokenProvider: JwtTokenProvider,
    private val authenticationManagerBuilder: AuthenticationManagerBuilder,
    private val mailService: MailService,
    private val redisService: RedisService,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${spring.mail.auth-code-expiration-millis}")
    private val authCodeExpirationMillis: Long
) {
    companion object {
        private const val AUTH_CODE_PREFIX = "AuthCode "
        private const val VERIFIED_EMAIL_PREFIX = "VerifiedEmail "
    }

    fun login(request: MemberDto.LoginRequest): JwtToken {
        val authenticationToken = UsernamePasswordAuthenticationToken(request.email, request.password)
        val authentication = authenticationManagerBuilder.`object`.authenticate(authenticationToken)
        return jwtTokenProvider.generateToken(authentication)
    }

    fun reissue(request: MemberDto.ReissueRequest): JwtToken {
        jwtTokenProvider.validateToken(request.refreshToken)
        return jwtTokenProvider.reissueToken(request.accessToken, request.refreshToken)
    }

    fun signUp(request: MemberDto.SignUpRequest) {
        val isSuccess = redisService.getValues("$VERIFIED_EMAIL_PREFIX${request.email}")
            ?: throw BusinessException(ErrorCode.EMAIL_NOT_VERIFIED)

        if (isSuccess != "true") {
            throw BusinessException(ErrorCode.EMAIL_NOT_VERIFIED)
        }

        if (memberRepository.existsByEmail(request.email)) {
            throw BusinessException(ErrorCode.ALREADY_SIGNED_EMAIL)
        }

        val member = Member(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            nickname = request.nickname,
            provider = Provider.LOCAL,
            role = Role.USER,
            isFirstLogin = false
        )
        memberRepository.save(member)
    }

    fun sendCodeToEmail(toEmail: String) {
        checkDuplicatedEmail(toEmail)
        val title = "Tribe 이메일 인증 번호"
        val authCode = createCode()
        mailService.sendEmail(toEmail, title, authCode)
        redisService.setValues("$AUTH_CODE_PREFIX$toEmail", authCode, Duration.ofMillis(authCodeExpirationMillis))
    }

    fun logout(email: String) {
        if (redisService.getValues("RT:$email") == null) {
            throw BusinessException(ErrorCode.ALREADY_LOGGED_OUT)
        }
        redisService.deleteValues("RT:$email")
    }

    fun checkDuplicatedNickname(request: MemberDto.VerifiedNicknameRequest) {
        if (memberRepository.existsByNickname(request.nickname)) {
            throw BusinessException(ErrorCode.DUPLICATE_NICKNAME)
        }
    }

    fun verifiedCode(request: MemberDto.VerifiedRequest) {
        val (email, authCode) = request // data class의 구조 분해 선언
        checkDuplicatedEmail(email)
        val redisAuthCode = redisService.getValues("$AUTH_CODE_PREFIX$email")
            ?: throw BusinessException(ErrorCode.AUTH_CODE_NOT_FOUND)

        if (redisAuthCode != authCode) {
            throw BusinessException(ErrorCode.AUTH_CODE_MISMATCH)
        }

        redisService.setValues("$VERIFIED_EMAIL_PREFIX$email", "true")
    }

    private fun checkDuplicatedEmail(email: String) {
        if (memberRepository.existsByEmail(email)) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }
    }

    private fun createCode(): String {
        return (1..6).joinToString("") {
            Random.nextInt(0, 10).toString()
        }
    }
}