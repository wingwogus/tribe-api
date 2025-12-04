package com.tribe.tribe_api.chat.dto

import com.tribe.tribe_api.chat.entity.ChatMessage
import com.tribe.tribe_api.trip.dto.TripMemberDto

sealed class ChatMessageDto {
    data class Request(
        val content: String
    )

    data class Response(
        val messageId: Long,
        val sender: TripMemberDto.Details,
        val content: String,
        val timestamp: String
    ) {
        companion object {
            fun from(chatMessage: ChatMessage): Response {
                val sender = TripMemberDto.Details.from(chatMessage.sender)

                return Response(
                    messageId = chatMessage.id!!,
                    sender = sender,
                    content = chatMessage.content,
                    timestamp = chatMessage.createdAt.toString()
                )
            }
        }
    }

    data class ChatHistoryResponse(
        val content: List<Response>,
        val nextCursor: String?,
        val hasNext: Boolean
    )
}

