package com.tribe.tribe_api.common.util.jwt

import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.security.CustomUserDetailsService
import com.tribe.tribe_api.common.util.service.RedisService
import io.jsonwebtoken.*
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import java.security.Key
import java.time.Duration
import java.util.*

@Component
class JwtTokenProvider(
    // 주 생성자에서 의존성 주입 및 초기화
    @Value("\${jwt.secret}") secretKey: String,
    private val redisService: RedisService,
    private val customUserDetailsService: CustomUserDetailsService
) {
    private val key: Key

    // 초기화 블록
    init {
        val keyBytes = Decoders.BASE64.decode(secretKey)
        this.key = Keys.hmacShaKeyFor(keyBytes)
    }

    // Slf4j 대신 companion object를 이용한 로거
    private val log = LoggerFactory.getLogger(this::class.java)

    // Member 정보를 가지고 AccessToken, RefreshToken을 생성하는 메서드
    fun generateToken(authentication: Authentication): JwtToken {
        val now = Date().time
        val accessToken = generateAccessToken(authentication, now)
        val refreshToken = generateRefreshToken(authentication, now)

        return JwtToken("Bearer", accessToken, refreshToken)
    }

    fun reissueToken(accessToken: String, refreshToken: String): JwtToken {
        validateToken(refreshToken)
        val authentication = getAuthentication(accessToken)

        val storedRefreshToken = redisService.getValues("RT:${authentication.name}")
            ?: throw BusinessException(ErrorCode.INVALID_TOKEN)

        if (storedRefreshToken != refreshToken) {
            throw BusinessException(ErrorCode.INVALID_TOKEN)
        }

        val now = Date().time
        val newAccessToken = generateAccessToken(authentication, now)
        var newRefreshToken = refreshToken

        val refreshTokenExpiration = parseClaims(refreshToken).expiration.time
        if (refreshTokenExpiration - now < Duration.ofDays(3).toMillis()) {
            log.info("리프레쉬 토큰 재발급")
            newRefreshToken = generateRefreshToken(authentication, now)
        }

        return JwtToken("Bearer", newAccessToken, newRefreshToken)
    }

    private fun generateAccessToken(authentication: Authentication, now: Long): String {
        val authorities = authentication.authorities.joinToString(",") { it.authority }

        return Jwts.builder()
            .setSubject(authentication.name)
            .claim("auth", authorities)
            .setExpiration(Date(now + 60 * 60 * 1000L)) // 1시간
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    private fun generateRefreshToken(authentication: Authentication, now: Long): String {
        return Jwts.builder()
            .setExpiration(Date(now + 7 * 24 * 60 * 60 * 1000L)) // 7일
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
            .also { // also 확장 함수: 생성된 refreshToken으로 추가 작업
                redisService.setValues("RT:${authentication.name}", it, Duration.ofDays(7))
            }
    }

    fun getAuthentication(accessToken: String): Authentication {
        val claims = parseClaims(accessToken)
        val auth = claims["auth"] ?: throw BusinessException(ErrorCode.INVALID_TOKEN)

        val authorities = auth.toString().split(",").map(::SimpleGrantedAuthority)
        val principal = customUserDetailsService.loadUserByUsername(claims.subject)

        return UsernamePasswordAuthenticationToken(principal, "", authorities)
    }

    fun validateToken(token: String) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)
        } catch (e: Exception) {
            // Kotlin의 when을 사용하여 예외 타입을 깔끔하게 처리
            when (e) {
                is SecurityException, is MalformedJwtException, is IllegalArgumentException ->
                    throw BusinessException(ErrorCode.INVALID_TOKEN)
                is ExpiredJwtException ->
                    throw BusinessException(ErrorCode.EXPIRED_TOKEN)
                is UnsupportedJwtException ->
                    throw BusinessException(ErrorCode.UNSUPPORTED_TOKEN)
                else -> throw BusinessException(ErrorCode.UNKNOWN_TOKEN_ERROR)
            }
        }
    }

    private fun parseClaims(token: String): Claims {
        return try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).body
        } catch (e: ExpiredJwtException) {
            e.claims
        }
    }
}