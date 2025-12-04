package com.tribe.tribe_api.chat.repository

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import com.tribe.tribe_api.chat.entity.ChatMessage
import com.tribe.tribe_api.chat.entity.QChatMessage.chatMessage
import com.tribe.tribe_api.common.util.cursor.CursorCodec
import com.tribe.tribe_api.member.entity.QMember.member
import com.tribe.tribe_api.trip.entity.QTripMember.tripMember
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl

class ChatMessageRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : ChatMessageRepositoryCustom {

    override fun findChatHistory(tripId: Long, cursor: CursorCodec.Parsed?, pageSize: Int): Slice<ChatMessage> {
        val limit = pageSize + 1

        val content = queryFactory
            .selectFrom(chatMessage)
            .join(chatMessage.sender, tripMember).fetchJoin()
            .leftJoin(tripMember.member, member).fetchJoin()
            .where(
                chatMessage.trip.id.eq(tripId),
                ltCursor(cursor)
            )
            .orderBy(chatMessage.createdAt.desc(), chatMessage.id.desc()) // 시간 -> ID 순 정렬
            .limit(limit.toLong())
            .fetch()

        var hasNext = false
        if (content.size > pageSize) {
            content.removeAt(pageSize)
            hasNext = true
        }

        return SliceImpl(content, PageRequest.of(0, pageSize), hasNext)
    }

    // CursorCodec.Parsed를 이용한 동적 쿼리 조건
    private fun ltCursor(cursor: CursorCodec.Parsed?): BooleanExpression? {
        if (cursor == null) return null

        // 생성 시간이 커서보다 작거나 (과거)
        // 생성 시간이 같으면서 ID가 커서보다 작은 경우 (같은 시간대 메시지 처리)
        return chatMessage.createdAt.lt(cursor.createdAt)
            .or(
                chatMessage.createdAt.eq(cursor.createdAt)
                    .and(chatMessage.id.lt(cursor.id))
            )
    }
}