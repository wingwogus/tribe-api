package com.tribe.tribe_api.itinerary.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.tribe.tribe_api.common.util.service.GoogleMapService
import com.tribe.tribe_api.common.util.service.RedisService
import com.tribe.tribe_api.itinerary.dto.GoogleDto
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@SpringBootTest
class GoogleMapServiceTest {

    @Autowired
    private lateinit var googleMapService: GoogleMapService

    @MockkBean(relaxed = true)
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var redisService: RedisService

    @MockkBean(relaxed = true)
    private lateinit var webClient: WebClient

    // --- 공용 데이터 ---
    private val query = "오사카 라멘"
    private val language = "ko"
    private val region = "JP"
    private val searchKey = "$query-$region-$language"
    private val cacheKey = "search_cache:$searchKey"

    private val mockPlace = GoogleDto.GoogleApiResponse.PlaceResult(
        id = "place123",
        displayName = GoogleDto.GoogleApiResponse.DisplayName(text = "이치란 라멘", languageCode = "ko"),
        formattedAddress = "일본 오사카 어딘가",
        location = GoogleDto.GoogleApiResponse.Location(latitude = 34.6684, longitude = 135.5023)
    )
    private val mockApiResponse = GoogleDto.GoogleApiResponse(places = listOf(mockPlace))
    private val emptyApiResponse = GoogleDto.GoogleApiResponse(places = null)

    private lateinit var mockMono: Mono<GoogleDto.GoogleApiResponse>
    private lateinit var mockMonoError: Mono<GoogleDto.GoogleApiResponse>

    @BeforeEach
    fun setUp() {
        mockMono = mockk(relaxed = true)
        mockMonoError = mockk(relaxed = true)

        every {
            webClient.post()
                .uri(any<String>())
                .header(any(), any())
                .header(any(), any())
                .bodyValue(any())
                .retrieve()
                .bodyToMono(GoogleDto.GoogleApiResponse::class.java)
                .doOnError(any())
                .onErrorReturn(any())
        } returns mockMono
    }

    @Test
    @DisplayName("장소 검색 - Cache HIT (캐시 데이터 반환)")
    fun 장소_검색_캐시_히트_테스트() {
        // given
        val cachedData = """{"places": [...]}"""
        every { redisService.getValues(cacheKey) } returns cachedData
        every {
            objectMapper.readValue(eq(cachedData), eq(GoogleDto.GoogleApiResponse::class.java))
        } returns mockApiResponse

        // when
        val result = googleMapService.searchPlaces(query, language, region)

        // then
        println("[Cache HIT] 테스트 결과: $result")

        assertThat(result).isNotNull()
        assertThat(result).hasSize(1)
        assertThat(result[0].externalPlaceId).isEqualTo("place123")
        assertThat(result[0].placeName).isEqualTo("이치란 라멘")

        verify(exactly = 1) { redisService.getValues(cacheKey) }
        verify(exactly = 1) { objectMapper.readValue(eq(cachedData), eq(GoogleDto.GoogleApiResponse::class.java)) }
        verify(exactly = 0) { webClient.post() }
        verify(exactly = 0) { redisService.setGoogleApiData(any(), any()) }
    }

    @Test
    @DisplayName("장소 검색 - Cache MISS (API 호출 및 캐시 저장)")
    fun 장소_검색_캐시_미스_테스트() {
        // given
        every { redisService.getValues(cacheKey) } returns null
        every { mockMono.block() } returns mockApiResponse
        every { redisService.setGoogleApiData(eq(searchKey), any<GoogleDto.GoogleApiResponse>()) } just runs

        // when
        val result = googleMapService.searchPlaces(query, language, region)

        // then
        println("[Cache MISS] 테스트 결과: $result")

        assertThat(result).isNotNull()
        assertThat(result).hasSize(1)
        assertThat(result[0].externalPlaceId).isEqualTo("place123")

        verify(exactly = 1) { redisService.getValues(cacheKey) }
        verify(exactly = 1) { webClient.post() }
        verify(exactly = 1) { redisService.setGoogleApiData(eq(searchKey), eq(mockApiResponse)) }
        verify(exactly = 0) { objectMapper.readValue(any<String>(), any<Class<*>>()) }
    }

    @Test
    @DisplayName("장소 검색 - Cache MISS 및 API 에러/빈 응답")
    fun 장소_검색_캐시_미스_API_에러_테스트() {
        // given
        every { redisService.getValues(cacheKey) } returns null
        every {
            webClient.post()
                .uri(any<String>())
                .header(any(), any())
                .header(any(), any())
                .bodyValue(any())
                .retrieve()
                .bodyToMono(GoogleDto.GoogleApiResponse::class.java)
                .doOnError(any())
                .onErrorReturn(any())
        } returns mockMonoError
        every { mockMonoError.block() } returns emptyApiResponse

        // when
        val result = googleMapService.searchPlaces(query, language, region)

        // then
        println(" [Cache MISS + API Error] 테스트 결과: $result")

        assertThat(result).isNotNull()
        assertThat(result).isEmpty()

        verify(exactly = 1) { redisService.getValues(cacheKey) }
        verify(exactly = 1) { webClient.post() }
        verify(exactly = 0) { redisService.setGoogleApiData(any(), any()) }
        verify(exactly = 0) { objectMapper.readValue(any<String>(), any<Class<*>>()) }
    }
}