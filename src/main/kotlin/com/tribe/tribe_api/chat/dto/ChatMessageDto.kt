package com.tribe.tribe_api.chat.dto

import com.tribe.tribe_api.chat.entity.ChatMessage

sealed class ChatMessageDto {
    data class Request(
        val content: String
    )

    data class Response(
        val messageId: Long,
        val senderId: Long,
        val nickname: String,
        val avatar: String?,
        val content: String,
        val timestamp: String
    ) {
        companion object {
            fun from(chatMessage: ChatMessage): Response {
                val sender = chatMessage.sender

                return Response(
                    messageId = chatMessage.id!!,
                    senderId = sender.id!!,
                    nickname = sender.name,
                    avatar = sender.member?.avatar,
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

