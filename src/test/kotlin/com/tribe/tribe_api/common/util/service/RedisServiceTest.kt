package com.tribe.tribe_api.common.util.service

/*
Testing stack:
- Framework: JUnit 5 (useJUnitPlatform)
- Mocking: MockK (io.mockk)
- Assertions: AssertJ (via spring-boot-starter-test)
*/

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.tribe.tribe_api.itinerary.dto.GoogleDto
import io.mockk.*
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.concurrent.TimeUnit

@ExtendWith(MockKExtension::class)
@DisplayName("RedisService")
class RedisServiceTest {

    @MockK
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @MockK
    private lateinit var objectMapper: ObjectMapper

    @MockK
    private lateinit var valueOperations: ValueOperations<String, String>

    @MockK
    private lateinit var hashOperations: HashOperations<String, String, String>

    private lateinit var redisService: RedisService

    @BeforeEach
    fun setUp() {
        every { redisTemplate.opsForValue() } returns valueOperations
        every { redisTemplate.opsForHash<String, String>() } returns hashOperations
        redisService = RedisService(redisTemplate, objectMapper)
    }

    @Nested
    @DisplayName("setGoogleApiData")
    inner class SetGoogleApiData {

        @Test
        fun `should set Google API data with correct key and TTL`() {
            val searchKey = "test-search"
            val expectedKey = "search_cache:test-search"
            val mockApiResponse = mockk<GoogleDto.GoogleApiResponse>()
            val serializedData = """{"status":"OK","results":[]}"""
            val expectedDuration = Duration.ofDays(7)

            every { objectMapper.writeValueAsString(mockApiResponse) } returns serializedData
            every { valueOperations.set(expectedKey, serializedData, expectedDuration) } just Runs

            redisService.setGoogleApiData(searchKey, mockApiResponse)

            verify(exactly = 1) { objectMapper.writeValueAsString(mockApiResponse) }
            verify(exactly = 1) { valueOperations.set(expectedKey, serializedData, expectedDuration) }
        }

        @Test
        fun `should handle empty search key`() {
            val searchKey = ""
            val expectedKey = "search_cache:"
            val mockApiResponse = mockk<GoogleDto.GoogleApiResponse>()
            val serializedData = """{"status":"OK","results":[]}"""

            every { objectMapper.writeValueAsString(mockApiResponse) } returns serializedData
            every { valueOperations.set(expectedKey, serializedData, Duration.ofDays(7)) } just Runs

            redisService.setGoogleApiData(searchKey, mockApiResponse)

            verify { valueOperations.set(expectedKey, serializedData, Duration.ofDays(7)) }
        }

        @Test
        fun `should propagate JsonProcessingException when serialization fails`() {
            val searchKey = "test-search"
            val mockApiResponse = mockk<GoogleDto.GoogleApiResponse>()
            val exception = object : JsonProcessingException("Serialization failed") {}

            every { objectMapper.writeValueAsString(mockApiResponse) } throws exception

            assertThrows<JsonProcessingException> {
                redisService.setGoogleApiData(searchKey, mockApiResponse)
            }

            verify(exactly = 0) { valueOperations.set(any(), any(), any<Duration>()) }
        }

        @Test
        fun `should handle special characters in search key`() {
            val searchKey = "test@search#key:with/special\\chars"
            val expectedKey = "search_cache:test@search#key:with/special\\chars"
            val mockApiResponse = mockk<GoogleDto.GoogleApiResponse>()
            val serializedData = """{"status":"OK"}"""

            every { objectMapper.writeValueAsString(mockApiResponse) } returns serializedData
            every { valueOperations.set(expectedKey, serializedData, Duration.ofDays(7)) } just Runs

            redisService.setGoogleApiData(searchKey, mockApiResponse)

            verify { valueOperations.set(expectedKey, serializedData, Duration.ofDays(7)) }
        }
    }

    @Nested
    @DisplayName("setValues")
    inner class SetValues {

        @Test
        fun `should set key-value without TTL`() {
            val key = "test-key"
            val data = "test-data"

            every { valueOperations.set(key, data) } just Runs

            redisService.setValues(key, data)

            verify(exactly = 1) { valueOperations.set(key, data) }
        }

        @Test
        fun `should set key-value with duration TTL`() {
            val key = "test-key"
            val data = "test-data"
            val duration = Duration.ofMinutes(30)

            every { valueOperations.set(key, data, duration) } just Runs

            redisService.setValues(key, data, duration)

            verify(exactly = 1) { valueOperations.set(key, data, duration) }
        }

        @Test
        fun `should handle empty key gracefully`() {
            val emptyKey = ""
            val data = "test-data"

            every { valueOperations.set(emptyKey, data) } just Runs

            redisService.setValues(emptyKey, data)

            verify { valueOperations.set(emptyKey, data) }
        }

        @Test
        fun `should handle empty data gracefully`() {
            val key = "test-key"
            val emptyData = ""

            every { valueOperations.set(key, emptyData) } just Runs

            redisService.setValues(key, emptyData)

            verify { valueOperations.set(key, emptyData) }
        }

        @Test
        fun `should handle zero duration`() {
            val key = "test-key"
            val data = "test-data"
            val zeroDuration = Duration.ZERO

            every { valueOperations.set(key, data, zeroDuration) } just Runs

            redisService.setValues(key, data, zeroDuration)

            verify { valueOperations.set(key, data, zeroDuration) }
        }

        @Test
        fun `should handle negative duration`() {
            val key = "test-key"
            val data = "test-data"
            val negativeDuration = Duration.ofMinutes(-10)

            every { valueOperations.set(key, data, negativeDuration) } just Runs

            redisService.setValues(key, data, negativeDuration)

            verify { valueOperations.set(key, data, negativeDuration) }
        }
    }

    @Nested
    @DisplayName("getValues")
    inner class GetValues {

        @Test
        fun `should return value when key exists`() {
            val key = "existing-key"
            val expectedValue = "existing-value"

            every { valueOperations.get(key) } returns expectedValue

            val result = redisService.getValues(key)

            assertThat(result).isEqualTo(expectedValue)
            verify(exactly = 1) { valueOperations.get(key) }
        }

        @Test
        fun `should return null when key does not exist`() {
            val key = "non-existing-key"

            every { valueOperations.get(key) } returns null

            val result = redisService.getValues(key)

            assertThat(result).isNull()
            verify(exactly = 1) { valueOperations.get(key) }
        }

        @Test
        fun `should handle empty key`() {
            val emptyKey = ""

            every { valueOperations.get(emptyKey) } returns null

            val result = redisService.getValues(emptyKey)

            assertThat(result).isNull()
            verify { valueOperations.get(emptyKey) }
        }

        @Test
        fun `should return empty string value correctly`() {
            val key = "empty-value-key"
            val emptyValue = ""

            every { valueOperations.get(key) } returns emptyValue

            val result = redisService.getValues(key)

            assertThat(result).isEqualTo(emptyValue)
            verify { valueOperations.get(key) }
        }
    }

    @Nested
    @DisplayName("deleteValues")
    inner class DeleteValues {

        @Test
        fun `should delete existing key`() {
            val key = "key-to-delete"

            every { redisTemplate.delete(key) } returns true

            redisService.deleteValues(key)

            verify(exactly = 1) { redisTemplate.delete(key) }
        }

        @Test
        fun `should handle deletion of non-existing key`() {
            val key = "non-existing-key"

            every { redisTemplate.delete(key) } returns false

            redisService.deleteValues(key)

            verify(exactly = 1) { redisTemplate.delete(key) }
        }

        @Test
        fun `should handle empty key deletion`() {
            val emptyKey = ""

            every { redisTemplate.delete(emptyKey) } returns false

            redisService.deleteValues(emptyKey)

            verify { redisTemplate.delete(emptyKey) }
        }
    }

    @Nested
    @DisplayName("expireValues")
    inner class ExpireValues {

        @Test
        fun `should set expiration with seconds`() {
            val key = "expiring-key"
            val timeout = 300L
            val timeUnit = TimeUnit.SECONDS

            every { redisTemplate.expire(key, timeout, timeUnit) } returns true

            redisService.expireValues(key, timeout, timeUnit)

            verify(exactly = 1) { redisTemplate.expire(key, timeout, timeUnit) }
        }

        @Test
        fun `should set expiration with minutes`() {
            val key = "expiring-key"
            val timeout = 30L
            val timeUnit = TimeUnit.MINUTES

            every { redisTemplate.expire(key, timeout, timeUnit) } returns true

            redisService.expireValues(key, timeout, timeUnit)

            verify { redisTemplate.expire(key, timeout, timeUnit) }
        }

        @Test
        fun `should handle zero timeout`() {
            val key = "expiring-key"
            val timeout = 0L
            val timeUnit = TimeUnit.SECONDS

            every { redisTemplate.expire(key, timeout, timeUnit) } returns true

            redisService.expireValues(key, timeout, timeUnit)

            verify { redisTemplate.expire(key, timeout, timeUnit) }
        }

        @Test
        fun `should handle negative timeout`() {
            val key = "expiring-key"
            val timeout = -1L
            val timeUnit = TimeUnit.SECONDS

            every { redisTemplate.expire(key, timeout, timeUnit) } returns false

            redisService.expireValues(key, timeout, timeUnit)

            verify { redisTemplate.expire(key, timeout, timeUnit) }
        }

        @Test
        fun `should handle non-existing key expiration`() {
            val key = "non-existing-key"
            val timeout = 300L
            val timeUnit = TimeUnit.SECONDS

            every { redisTemplate.expire(key, timeout, timeUnit) } returns false

            redisService.expireValues(key, timeout, timeUnit)

            verify { redisTemplate.expire(key, timeout, timeUnit) }
        }
    }

    @Nested
    @DisplayName("setHashOps")
    inner class SetHashOps {

        @Test
        fun `should set hash with multiple key-value pairs`() {
            val key = "hash-key"
            val data = mapOf(
                "field1" to "value1",
                "field2" to "value2",
                "field3" to "value3"
            )

            every { hashOperations.putAll(key, data) } just Runs

            redisService.setHashOps(key, data)

            verify(exactly = 1) { hashOperations.putAll(key, data) }
        }

        @Test
        fun `should handle empty hash map`() {
            val key = "hash-key"
            val emptyData = emptyMap<String, String>()

            every { hashOperations.putAll(key, emptyData) } just Runs

            redisService.setHashOps(key, emptyData)

            verify { hashOperations.putAll(key, emptyData) }
        }

        @Test
        fun `should handle hash with single key-value pair`() {
            val key = "hash-key"
            val singleData = mapOf("single-field" to "single-value")

            every { hashOperations.putAll(key, singleData) } just Runs

            redisService.setHashOps(key, singleData)

            verify { hashOperations.putAll(key, singleData) }
        }

        @Test
        fun `should handle hash with empty values`() {
            val key = "hash-key"
            val dataWithEmptyValues = mapOf(
                "field1" to "",
                "field2" to "value2"
            )

            every { hashOperations.putAll(key, dataWithEmptyValues) } just Runs

            redisService.setHashOps(key, dataWithEmptyValues)

            verify { hashOperations.putAll(key, dataWithEmptyValues) }
        }
    }

    @Nested
    @DisplayName("getHashOps")
    inner class GetHashOps {

        @Test
        fun `should return hash value when key and hashKey exist`() {
            val key = "hash-key"
            val hashKey = "field1"
            val expectedValue = "value1"

            every { hashOperations.get(key, hashKey) } returns expectedValue

            val result = redisService.getHashOps(key, hashKey)

            assertThat(result).isEqualTo(expectedValue)
            verify(exactly = 1) { hashOperations.get(key, hashKey) }
        }

        @Test
        fun `should return null when hash key does not exist`() {
            val key = "hash-key"
            val hashKey = "non-existing-field"

            every { hashOperations.get(key, hashKey) } returns null

            val result = redisService.getHashOps(key, hashKey)

            assertThat(result).isNull()
            verify { hashOperations.get(key, hashKey) }
        }

        @Test
        fun `should return null when main key does not exist`() {
            val key = "non-existing-hash-key"
            val hashKey = "field1"

            every { hashOperations.get(key, hashKey) } returns null

            val result = redisService.getHashOps(key, hashKey)

            assertThat(result).isNull()
            verify { hashOperations.get(key, hashKey) }
        }

        @Test
        fun `should handle empty string values correctly`() {
            val key = "hash-key"
            val hashKey = "empty-field"
            val emptyValue = ""

            every { hashOperations.get(key, hashKey) } returns emptyValue

            val result = redisService.getHashOps(key, hashKey)

            assertThat(result).isEqualTo(emptyValue)
            verify { hashOperations.get(key, hashKey) }
        }

        @Test
        fun `should handle empty key and hashKey`() {
            val emptyKey = ""
            val emptyHashKey = ""

            every { hashOperations.get(emptyKey, emptyHashKey) } returns null

            val result = redisService.getHashOps(emptyKey, emptyHashKey)

            assertThat(result).isNull()
            verify { hashOperations.get(emptyKey, emptyHashKey) }
        }
    }

    @Nested
    @DisplayName("deleteHashOps")
    inner class DeleteHashOps {

        @Test
        fun `should delete single hash field`() {
            val key = "hash-key"
            val hashKey = "field1"

            every { hashOperations.delete(key, hashKey) } returns 1L

            redisService.deleteHashOps(key, hashKey)

            verify(exactly = 1) { hashOperations.delete(key, hashKey) }
        }

        @Test
        fun `should delete multiple hash fields`() {
            val key = "hash-key"
            val hashKeys = arrayOf("field1", "field2", "field3")

            every { hashOperations.delete(key, *hashKeys) } returns 3L

            redisService.deleteHashOps(key, *hashKeys)

            verify(exactly = 1) { hashOperations.delete(key, *hashKeys) }
        }

        @Test
        fun `should handle deletion of non-existing hash fields`() {
            val key = "hash-key"
            val hashKeys = arrayOf("non-existing-field1", "non-existing-field2")

            every { hashOperations.delete(key, *hashKeys) } returns 0L

            redisService.deleteHashOps(key, *hashKeys)

            verify { hashOperations.delete(key, *hashKeys) }
        }

        @Test
        fun `should handle empty hash keys array`() {
            val key = "hash-key"
            val emptyHashKeys = emptyArray<String>()

            every { hashOperations.delete(key, *emptyHashKeys) } returns 0L

            redisService.deleteHashOps(key, *emptyHashKeys)

            verify { hashOperations.delete(key, *emptyHashKeys) }
        }

        @Test
        fun `should handle mixed existing and non-existing hash fields`() {
            val key = "hash-key"
            val hashKeys = arrayOf("existing-field", "non-existing-field")

            every { hashOperations.delete(key, *hashKeys) } returns 1L

            redisService.deleteHashOps(key, *hashKeys)

            verify { hashOperations.delete(key, *hashKeys) }
        }
    }

    @Nested
    @DisplayName("Integration scenarios")
    inner class IntegrationScenarios {

        @Test
        fun `should handle Redis connection failure gracefully`() {
            val key = "test-key"
            val data = "test-data"
            val exception = RuntimeException("Redis connection failed")

            every { valueOperations.set(key, data) } throws exception

            assertThrows<RuntimeException> {
                redisService.setValues(key, data)
            }
        }

        @Test
        fun `should handle serialization and Redis operations in sequence`() {
            val searchKey = "complex-search"
            val apiResponse = mockk<GoogleDto.GoogleApiResponse>()
            val serializedData = """{"complex":"data","array":[1,2,3]}"""
            val cacheKey = "search_cache:complex-search"

            every { objectMapper.writeValueAsString(apiResponse) } returns serializedData
            every { valueOperations.set(cacheKey, serializedData, Duration.ofDays(7)) } just Runs

            redisService.setGoogleApiData(searchKey, apiResponse)

            verifyOrder {
                objectMapper.writeValueAsString(apiResponse)
                valueOperations.set(cacheKey, serializedData, Duration.ofDays(7))
            }
        }

        @Test
        fun `should handle concurrent access patterns`() {
            val key = "concurrent-key"
            val data1 = "data1"
            val data2 = "data2"

            every { valueOperations.set(key, data1) } just Runs
            every { valueOperations.set(key, data2) } just Runs
            every { valueOperations.get(key) } returns data2

            redisService.setValues(key, data1)
            redisService.setValues(key, data2)
            val result = redisService.getValues(key)

            assertThat(result).isEqualTo(data2)
            verify { valueOperations.set(key, data1) }
            verify { valueOperations.set(key, data2) }
            verify { valueOperations.get(key) }
        }
    }

    @Nested
    @DisplayName("Edge cases and boundary conditions")
    inner class EdgeCases {

        @Test
        fun `should handle very long keys`() {
            val longKey = "a".repeat(1000)
            val data = "test-data"

            every { valueOperations.set(longKey, data) } just Runs

            redisService.setValues(longKey, data)

            verify { valueOperations.set(longKey, data) }
        }

        @Test
        fun `should handle very long data values`() {
            val key = "test-key"
            val longData = "x".repeat(10000)

            every { valueOperations.set(key, longData) } just Runs

            redisService.setValues(key, longData)

            verify { valueOperations.set(key, longData) }
        }

        @Test
        fun `should handle Unicode characters in keys and values`() {
            val unicodeKey = "测试键名_🔑"
            val unicodeData = "测试数据_📝"

            every { valueOperations.set(unicodeKey, unicodeData) } just Runs

            redisService.setValues(unicodeKey, unicodeData)

            verify { valueOperations.set(unicodeKey, unicodeData) }
        }

        @Test
        fun `should handle very large Duration values`() {
            val key = "large-duration-key"
            val data = "test-data"
            val largeDuration = Duration.ofDays(36500) // ~100 years

            every { valueOperations.set(key, data, largeDuration) } just Runs

            redisService.setValues(key, data, largeDuration)

            verify { valueOperations.set(key, data, largeDuration) }
        }

        @Test
        fun `should handle different TimeUnit values correctly`() {
            val key = "time-unit-key"
            val timeout = 1L

            every { redisTemplate.expire(key, timeout, TimeUnit.NANOSECONDS) } returns true
            every { redisTemplate.expire(key, timeout, TimeUnit.MICROSECONDS) } returns true
            every { redisTemplate.expire(key, timeout, TimeUnit.MILLISECONDS) } returns true
            every { redisTemplate.expire(key, timeout, TimeUnit.SECONDS) } returns true
            every { redisTemplate.expire(key, timeout, TimeUnit.MINUTES) } returns true
            every { redisTemplate.expire(key, timeout, TimeUnit.HOURS) } returns true
            every { redisTemplate.expire(key, timeout, TimeUnit.DAYS) } returns true

            TimeUnit.values().forEach { timeUnit ->
                redisService.expireValues(key, timeout, timeUnit)
                verify { redisTemplate.expire(key, timeout, timeUnit) }
            }
        }
    }
}