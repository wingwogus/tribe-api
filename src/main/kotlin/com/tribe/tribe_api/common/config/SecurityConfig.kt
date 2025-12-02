package com.tribe.tribe_api.common.config

import com.tribe.tribe_api.common.util.jwt.JwtAuthenticationFilter
import com.tribe.tribe_api.common.util.jwt.JwtExceptionFilter
import com.tribe.tribe_api.common.util.logger.MDCLoggingFilter
import com.tribe.tribe_api.common.util.oauth2.CustomOAuth2UserService
import com.tribe.tribe_api.common.util.oauth2.OAuth2LoginSuccessHandler
import com.tribe.tribe_api.common.util.security.CustomAuthenticationEntryPoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val jwtExceptionFilter: JwtExceptionFilter,
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val authenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    private val mdcLoggingFilter: MDCLoggingFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors{ it.configurationSource(corsConfigurationSource())}
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { it.authenticationEntryPoint(authenticationEntryPoint) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/",
                        "/.well-known/**",
                        "/api/v1/auth/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/ws-stomp/**"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .userInfoEndpoint { userInfo ->
                        userInfo.userService(customOAuth2UserService)
                    }
                    .successHandler(oAuth2LoginSuccessHandler)
            }
            .addFilterBefore(jwtExceptionFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(mdcLoggingFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    // 2. [중요] 구체적인 CORS 설정 (WebConfig의 내용을 여기로 옮김)
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()

        // 허용할 Origin (프론트엔드 주소)
        // 보안상 특정 도메인만 허용하는 것이 좋지만, 개발 중엔 "*" 사용 가능
        configuration.allowedOriginPatterns = listOf("*")

        // 허용할 HTTP Method
        configuration.allowedMethods = listOf("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")

        // 허용할 헤더
        configuration.allowedHeaders = listOf("*")

        // 자격 증명(쿠키, Authorization 헤더 등) 허용 여부
        configuration.allowCredentials = true

//        configuration.addExposedHeader("Authorization")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}