package com.tribe.tribe_api.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig: WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws-stomp") // 클라이언트 연결 엔드포인트
            .setAllowedOriginPatterns("*") // CORS 허용
            .withSockJS() // SockJS 지원
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // 클라이언트가 구독할 경로
        registry.enableSimpleBroker("/topic")

        // 클라이언트가 메시지를 보낼 경로
        registry.setApplicationDestinationPrefixes("/app")
    }
}