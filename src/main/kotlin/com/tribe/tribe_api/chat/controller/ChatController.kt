package com.tribe.tribe_api.chat.controller

import com.tribe.tribe_api.chat.dto.ChatMessageDto
import com.tribe.tribe_api.chat.service.ChatService
import com.tribe.tribe_api.common.util.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/trips/{tripId}/chat")
class ChatController(
    private val chatService: ChatService
) {
    @PostMapping
    fun sendChat(
        @PathVariable tripId: Long,
        @RequestBody request: ChatMessageDto.Request
    ): ResponseEntity<ApiResponse<Unit>> {
        chatService.sendChat(tripId, request.content)
        return ResponseEntity.ok(ApiResponse.success("메시지 전송 성공", null))
    }

    // 채팅 기록 조회 (무한 스크롤)
    // 예: GET /api/v1/trips/1/chat?cursorId=105&pageSize=20
    // 첫 조회: GET /api/v1/trips/1/chat?pageSize=20 (cursorId 생략)
    @GetMapping
    fun getChatHistory(
        @PathVariable tripId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): ResponseEntity<ApiResponse<ChatMessageDto.ChatHistoryResponse>> {
        val history = chatService.getChatHistory(tripId, cursor, pageSize)
        return ResponseEntity.ok(ApiResponse.success("채팅 기록 조회 성공", history))
    }
}