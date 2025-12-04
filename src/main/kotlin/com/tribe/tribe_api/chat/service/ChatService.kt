package com.tribe.tribe_api.chat.service

import com.tribe.tribe_api.chat.dto.ChatMessageDto
import com.tribe.tribe_api.chat.entity.ChatMessage
import com.tribe.tribe_api.chat.repository.ChatMessageRepository
import com.tribe.tribe_api.common.exception.BusinessException
import com.tribe.tribe_api.common.exception.ErrorCode
import com.tribe.tribe_api.common.util.cursor.CursorCodec
import com.tribe.tribe_api.common.util.security.SecurityUtil
import com.tribe.tribe_api.common.util.socket.SocketDto.EditType
import com.tribe.tribe_api.common.util.socket.SocketDto.TripEvent
import com.tribe.tribe_api.trip.repository.TripRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ChatService(
    private val chatMessageRepository: ChatMessageRepository, // 리포지토리 주입
    private val tripRepository: TripRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun sendChat(
        tripId: Long,
        content: String
    ): ChatMessageDto.Response {
        val memberId = SecurityUtil.getCurrentMemberId()

        val trip = tripRepository.findTripWithMembersById(tripId)
            ?: throw BusinessException(ErrorCode.TRIP_NOT_FOUND)

        val sender = trip.members.find { tripMember -> tripMember.member?.id == memberId }
            ?: throw BusinessException(ErrorCode.NOT_A_TRIP_MEMBER)

        val savedMessage = chatMessageRepository.save(
            ChatMessage(
                trip,
                sender,
                content
            )
        )

        val chatData = ChatMessageDto.Response.from(savedMessage)

        // 이벤트 발행 -> WebSocket 전송
        eventPublisher.publishEvent(
            TripEvent(
                tripId = tripId,
                senderId = memberId,
                type = EditType.CHAT,
                data = chatData
            )
        )

        return chatData
    }

    // 채팅 기록 조회 (CursorCodec 적용)
    @Transactional(readOnly = true)
    @PreAuthorize("@tripSecurityService.isTripMember(#tripId)")
    fun getChatHistory(
        tripId: Long,
        cursor: String?,
        pageSize: Int
    ): ChatMessageDto.ChatHistoryResponse {
        // 1. 커서 디코딩 (String -> CursorCodec.Parsed)
        // cursor가 null이면 decode 결과도 null (첫 페이지 조회)
        val parsedCursor = CursorCodec.decode(cursor)

        // 2. 리포지토리 호출 (parsedCursor 전달)
        // Repository의 findChatHistory 메서드 시그니처가 (Long, CursorCodec.Parsed?, Int)여야 함
        val slice = chatMessageRepository.findChatHistory(tripId, parsedCursor, pageSize)

        // 3. DTO 변환
        val content = slice.content.map { ChatMessageDto.Response.from(it) }

        // 4. 다음 커서 생성 (마지막 메시지 기준)
        var nextCursor: String? = null
        if (slice.hasNext() && slice.content.isNotEmpty()) {
            val lastMessage = slice.content.last()
            // 마지막 메시지의 시간과 ID로 커서 인코딩
            nextCursor = CursorCodec.encode(lastMessage.createdAt, lastMessage.id!!)
        }

        // 5. 응답 반환
        return ChatMessageDto.ChatHistoryResponse(
            content = content,
            nextCursor = nextCursor,
            hasNext = slice.hasNext()
        )
    }

}