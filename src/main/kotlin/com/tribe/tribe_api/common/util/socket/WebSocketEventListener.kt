package com.tribe.tribe_api.common.util.socket

import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

@Component
class WebSocketEventListener(
    private val messagingTemplate: SimpMessagingTemplate,
) {
    @EventListener
    fun handleTripEvent(event: SocketDto.TripEvent) {
        val message = SocketDto.TripEvent(
            type = event.type,
            tripId = event.tripId,
            senderId = event.senderId,
            data = event.data
        )

        // 기존 로직과 동일하게 해당 Trip 토픽으로 메시지 전송
        messagingTemplate.convertAndSend("/topic/trips/${event.tripId}", message)
    }
}