package com.tribe.tribe_api.common.util.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class RedisService(
    // RedisTemplate의 제네릭 타입을 <String, String>으로 명확히 하여 타입 안정성 확보
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    fun setValues(key: String, data: String) {
        redisTemplate.opsForValue().set(key, data)
    }

    fun setValues(key: String, data: String, duration: Duration) {
        redisTemplate.opsForValue().set(key, data, duration)
    }

    @Transactional(readOnly = true)
    fun getValues(key: String): String? { // Optional<String> -> String?
        return redisTemplate.opsForValue().get(key)
    }

    fun deleteValues(key: String) {
        redisTemplate.delete(key)
    }

    fun expireValues(key: String, timeout: Long, timeUnit: TimeUnit) {
        redisTemplate.expire(key, timeout, timeUnit)
    }

    fun setHashOps(key: String, data: Map<String, String>) {
        redisTemplate.opsForHash<String, String>().putAll(key, data)
    }

    @Transactional(readOnly = true)
    fun getHashOps(key: String, hashKey: String): String? { // 반환 타입을 Nullable로 변경
        return redisTemplate.opsForHash<String, String>().get(key, hashKey)
    }

    fun deleteHashOps(key: String, vararg hashKeys: String) {
        redisTemplate.opsForHash<String, String>().delete(key, *hashKeys)
    }
}