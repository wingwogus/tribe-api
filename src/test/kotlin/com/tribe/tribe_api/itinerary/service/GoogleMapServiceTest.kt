package com.tribe.tribe_api.itinerary.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.tribe.tribe_api.common.config.CloudinaryConfig
import com.tribe.tribe_api.common.util.service.CloudinaryUploadService
import com.tribe.tribe_api.common.util.service.GoogleMapService
import com.tribe.tribe_api.common.util.service.RedisService
import com.tribe.tribe_api.itinerary.dto.GoogleDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@SpringBootTest
class GoogleMapServiceTest {

    @Autowired
    private lateinit var googleMapService: GoogleMapService
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var redisService: RedisService

    @MockBean
    private lateinit var webClient: WebClient

    @MockBean
    private lateinit var cloudinaryConfig: CloudinaryConfig

    @MockBean
    private lateinit var cloudinaryUploadService: CloudinaryUploadService

    private val mockRequestBodyUriSpec = mock<WebClient.RequestBodyUriSpec>()
    private val mockRequestBodySpec = mock<WebClient.RequestBodySpec>()
    private val mockRequestHeadersSpec = mock<WebClient.RequestHeadersSpec<*>>()
    private val mockResponseSpec = mock<WebClient.ResponseSpec>()

    private val mockMono1 = mock<Mono<GoogleDto.GoogleApiResponse>>()
    private val mockMono2 = mock<Mono<GoogleDto.GoogleApiResponse>>()

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


    @BeforeEach
    fun setUp() {
        whenever(webClient.post()).thenReturn(mockRequestBodyUriSpec)
        whenever(mockRequestBodyUriSpec.uri(any<String>())).thenReturn(mockRequestBodySpec)
        whenever(mockRequestBodySpec.header(any(), any())).thenReturn(mockRequestBodySpec)
        whenever(mockRequestBodySpec.bodyValue(any())).thenReturn(mockRequestHeadersSpec)
        whenever(mockRequestHeadersSpec.retrieve()).thenReturn(mockResponseSpec)

        whenever(mockResponseSpec.bodyToMono(GoogleDto.GoogleApiResponse::class.java))
            .thenReturn(mockMono1)

        whenever(mockMono1.doOnError(any<((Throwable) -> Unit)>()))
            .thenReturn(mockMono2)
        whenever(mockMono2.onErrorReturn(any<GoogleDto.GoogleApiResponse>()))
            .thenReturn(mockMono2)
    }

    @Test
    @DisplayName("장소 검색 - Cache HIT (캐시 데이터 반환)")
    fun 장소_검색_캐시_히트_테스트() {
        // given
        val cachedData = objectMapper.writeValueAsString(mockApiResponse)
        whenever(redisService.getValues(cacheKey)).thenReturn(cachedData)

        // when
        val result = googleMapService.searchPlaces(query, language, region)

        // then
        assertThat(result).isNotNull()
        assertThat(result).hasSize(1)
        assertThat(result[0].placeId).isEqualTo(mockPlace.id)
        assertThat(result[0].placeName).isEqualTo(mockPlace.displayName?.text)


        verify(redisService, times(1)).getValues(cacheKey)
        verify(webClient, never()).post()
        verify(redisService, never()).setGoogleApiData(any(), any())
    }

    @Test
    @DisplayName("장소 검색 - Cache MISS (API 호출 및 캐시 저장)")
    fun 장소_검색_캐시_미스_테스트() {
        // given
        whenever(redisService.getValues(cacheKey)).thenReturn(null)

        whenever(mockMono2.block()).thenReturn(mockApiResponse)

        doNothing().whenever(redisService).setGoogleApiData(eq(searchKey), any<GoogleDto.GoogleApiResponse>())

        // when
        val result = googleMapService.searchPlaces(query, language, region)

        // then
        assertThat(result).isNotNull()
        assertThat(result).hasSize(1)
        assertThat(result[0].placeId).isEqualTo(mockPlace.id)
        verify(redisService, times(1)).getValues(cacheKey)
        verify(webClient, times(1)).post()
        verify(redisService, times(1)).setGoogleApiData(eq(searchKey), eq(mockApiResponse))
    }

    @Test
    @DisplayName("장소 검색 - Cache MISS 및 API 에러/빈 응답")
    fun 장소_검색_캐시_미스_API_에러_테스트() {
        // given
        val emptyApiResponse = GoogleDto.GoogleApiResponse(places = null)
        whenever(redisService.getValues(cacheKey)).thenReturn(null)

        whenever(mockMono2.block()).thenReturn(emptyApiResponse)
        // when
        val result = googleMapService.searchPlaces(query, language, region)

        // then
        assertThat(result).isNotNull()
        assertThat(result).isEmpty()
        verify(redisService, times(1)).getValues(cacheKey)
        verify(webClient, times(1)).post()
        verify(redisService, never()).setGoogleApiData(any(), any())
    }
}