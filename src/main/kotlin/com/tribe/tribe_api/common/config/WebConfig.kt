package com.tribe.tribe_api.common.config

import com.tribe.tribe_api.common.util.logger.LogInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.data.web.config.EnableSpringDataWebSupport
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
class WebConfig(
    val logInterceptor: LogInterceptor,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")
            .allowedMethods("*")
            .allowedHeaders("*")
            .allowCredentials(true)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(logInterceptor)
            .order(1) // 인터셉터 순서
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html"
            )
    }
}