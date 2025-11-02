package com.tribe.tribe_api.common.util.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.tribe.tribe_api.itinerary.dto.GoogleDto
import com.tribe.tribe_api.itinerary.dto.PlaceDto
import com.tribe.tribe_api.itinerary.entity.TravelMode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale.getDefault

@Service
class GoogleMapService(
    private val webClient: WebClient,
    @Value("\${google.maps.key}") private val apiKey: String,
    @Value("\${gemini.api.key}") private val apiKey2: String,
    private val redisService: RedisService,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun searchPlaces(
        query: String?,
        language: String,
        region: String?
    ): List<PlaceDto.Simple> {

        val searchKey = "$query-$region-$language"
        val cacheKey = "search_cache:$searchKey"

        val cachedData = redisService.getValues(cacheKey)
        if (cachedData != null) {
            logger.info("Cache HIT: key=$cacheKey")
            // 캐시 데이터가 있으면, JSON 문자열을 객체로 변환하여 바로 반환
            val googleResponse = objectMapper.readValue(cachedData, GoogleDto.PlacesResponse::class.java)
            return googleResponse.places?.map { PlaceDto.Simple.fromGoogle(it) } ?: emptyList()
        }


        logger.info("Cache MISS: key=$cacheKey. Calling Google API.")
        val responseMono: Mono<GoogleDto.PlacesResponse> = webClient.post()
            .uri("https://places.googleapis.com/v1/places:searchText")
            .header("X-Goog-Api-Key", apiKey)
            .header("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location")
            .bodyValue(
                mapOf(
                    "textQuery" to query,
                    "languageCode" to language,
                    "regionCode" to region
                )
            )
            .retrieve() // 요청 실행
            .bodyToMono(GoogleDto.PlacesResponse::class.java) // 응답 Body를 Mono<GoogleApiResponse>로 변환
            .doOnError { e -> logger.error("Error calling Google Places API", e) } // 에러 발생 시 로그 기록
            .onErrorReturn(GoogleDto.PlacesResponse(null)) // 에러 발생 시 기본값 반환

        // 비동기 작업이 끝날 때까지 기다렸다가 결과를 동기적으로 추출
        val googleResponse = responseMono.block()

        if (!googleResponse?.places.isNullOrEmpty()) {
            val searchKey = "$query-$region-$language"
            redisService.setGoogleApiData(searchKey, googleResponse!!)
        }

        return googleResponse?.places?.map {
            PlaceDto.Simple.fromGoogle(it)
        } ?: emptyList()
    }

    /**
     * 출발지와 도착지의 PlaceId(externalPlaceId)를 받아와서 mode에 따라 계산 후 JSON 형식의 String 반환
     * @param originPlaceId 출발지 placeId(externalPlaceId)
     * @param destinationPlaceId 도착지 placeId(externalPlaceId)
     * @param travelMode 거리 계산 방식(walking, drive)
     *
     */
    fun getDirections(
        originPlaceId: String,
        destinationPlaceId: String,
        travelMode: TravelMode
    ): GoogleDto.DirectionsRawResponse? {
        // 출발지ID:도착지ID:이동방식 으로 키 저장
        val mode = travelMode.name.lowercase(getDefault())

        val cacheKey = "directions_cache:$originPlaceId:$destinationPlaceId:$mode"
        val cachedData = redisService.getValues(cacheKey)

        // 캐싱 성공 시
        if (cachedData != null) {
            logger.info("Cache HIT for directions: key=$cacheKey")
            return objectMapper.readValue(
                cachedData,
                GoogleDto.DirectionsRawResponse::class.java
            )
        }

        // 캐싱 실패 시 Google Map Direction API 요청
        logger.info("Cache MISS for directions: key=$cacheKey. Calling Google Directions API.")
        val responseMono: Mono<GoogleDto.DirectionsRawResponse> = webClient.get()
            .uri { uriBuilder ->
                uriBuilder.scheme("https")
                    .host("maps.googleapis.com")
                    .path("/maps/api/directions/json")
                    .queryParam("origin", "place_id:$originPlaceId")
                    .queryParam("destination", "place_id:$destinationPlaceId")
                    .queryParam("language", "ko")
                    .queryParam("mode", mode)
                    .queryParam("key", apiKey2)
                    .build()
            }
            .retrieve()
            .bodyToMono(GoogleDto.DirectionsRawResponse::class.java)
            .doOnError { e -> logger.error("Error calling Google Directions API", e) }
            .onErrorReturn(GoogleDto.DirectionsRawResponse("not", mutableListOf())) // 에러 시 빈 String 반환

        val response = responseMono.block()

        if (response != null) {
            val duration = when (travelMode) {
                TravelMode.TRANSIT -> {
                    val now = LocalTime.now(ZoneId.of("Asia/Seoul"))
                    val endOfDawn = LocalTime.of(6, 0)

                    if (now.isBefore(endOfDawn)) {
                        // 새벽 시간 (00:00 ~ 05:59): 오전 6시까지 캐시
                        Duration.between(now, endOfDawn)
                    } else {
                        // 주간 시간 (06:00 ~ 23:59): 자정까지 캐시
                        Duration.between(now, LocalTime.MAX)
                    }
                }
                else -> Duration.ofDays(30)
            }

            redisService.setValues(cacheKey, objectMapper.writeValueAsString(response), duration)

            logger.info("Cached directions response for key=$cacheKey for $duration")
        }

        return response
    }
}