package com.tribe.tribe_api.chat.repository

import com.tribe.tribe_api.chat.entity.ChatMessage
import com.tribe.tribe_api.common.util.cursor.CursorCodec
import org.springframework.data.domain.Slice

interface ChatMessageRepositoryCustom {
    fun findChatHistory(tripId: Long, cursor: CursorCodec.Parsed?, pageSize: Int): Slice<ChatMessage>
}