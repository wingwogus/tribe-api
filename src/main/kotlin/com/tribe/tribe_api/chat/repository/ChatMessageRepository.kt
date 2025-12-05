package com.tribe.tribe_api.chat.repository

import com.tribe.tribe_api.chat.entity.ChatMessage
import org.springframework.data.jpa.repository.JpaRepository

interface ChatMessageRepository: JpaRepository<ChatMessage, Long>, ChatMessageRepositoryCustom {}