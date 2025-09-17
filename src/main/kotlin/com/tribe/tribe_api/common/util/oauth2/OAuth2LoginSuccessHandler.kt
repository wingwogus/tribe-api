package com.tribe.tribe_api.common.util.oauth2

import com.tribe.tribe_api.common.util.jwt.JwtTokenProvider
import com.tribe.tribe_api.common.util.security.CustomUserDetails
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.StandardCharsets

@Component
class OAuth2LoginSuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
    @Value("\${app.oauth.redirect-url}") private val redirectUrl: String
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oAuth2User = authentication.principal as CustomUserDetails
        val jwtToken = jwtTokenProvider.generateToken(authentication)
        val isNewUser = oAuth2User.member.isFirstLogin

        val targetUrl = UriComponentsBuilder.fromUriString(redirectUrl)
            .queryParam("accessToken", jwtToken.accessToken)
            .queryParam("refreshToken", jwtToken.refreshToken)
            .queryParam("isNewUser", isNewUser)
            .build()
            .encode(StandardCharsets.UTF_8)
            .toUriString()

        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}