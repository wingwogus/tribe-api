package com.tribe.tribe_api.common.util.cursor

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

object CursorCodec {
    fun encode(createdAt: LocalDateTime, id: Long): String {
        val epochMilli = createdAt.toInstant(ZoneOffset.UTC).toEpochMilli()
        val raw = "$epochMilli:$id"
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(cursor: String?): Parsed? {
        if (cursor == null || cursor.isBlank()) return null
        val raw = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
        val p = raw.split(":".toRegex()).dropLastWhile { it.isEmpty() }
        val epochMilli = p[0].toLong()

        return Parsed(
            Instant.ofEpochMilli(epochMilli).atZone(ZoneOffset.UTC).toLocalDateTime(),
            p[1].toLong()
        )
    }

    data class Parsed(
        val createdAt: LocalDateTime?,
        val id: Long?
    )
}
