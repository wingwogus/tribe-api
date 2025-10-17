package com.tribe.tribe_api.common.util.oauth2

import com.tribe.tribe_api.common.util.jwt.JwtTokenProvider
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.StandardCharsets
import java.time.Duration

@Component
class OAuth2LoginSuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
    @Value("\${app.url}") private val appUrl: String
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oAuth2User = authentication.principal as CustomUserDetails
        val jwtToken = jwtTokenProvider.generateToken(authentication)
        val isNewUser = oAuth2User.member.isFirstLogin

        val targetUrl = UriComponentsBuilder.fromUriString("$appUrl/oauth/callback")
            .build()
            .encode(StandardCharsets.UTF_8)
            .toUriString()

        // AccessToken 쿠키 설정
        val accessTokenCookie = ResponseCookie.from("accessToken", jwtToken.accessToken)
            .path("/")
            .secure(true)
            .sameSite("None") // 다른 도메인에서도 쿠키 전송 허용
            .maxAge(Duration.ofHours(1))
            .build()

        // RefreshToken 쿠키 설정
        val refreshTokenCookie = ResponseCookie.from("refreshToken", jwtToken.refreshToken)
            .path("/")
            .secure(true)
            .sameSite("None")
            .maxAge(Duration.ofDays(7))
            .build()

        // isNewUser 쿠키
        val isNewUserCookie = ResponseCookie.from("isNewUser", isNewUser.toString())
            .path("/")
            .secure(true)
            .sameSite("None")
            .maxAge(Duration.ofMinutes(1)) // 짧은 만료 시간 설정
            .build()

        response.addHeader("Set-Cookie", accessTokenCookie.toString())
        response.addHeader("Set-Cookie", refreshTokenCookie.toString())
        response.addHeader("Set-Cookie", isNewUserCookie.toString())

        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}