package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.itinerary.dto.GoogleDto
import com.tribe.tribe_api.itinerary.dto.PlaceDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Service
class GoogleMapService(
    private val webClient: WebClient,
    @Value("\${google.key}") private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun searchPlaces(
        query: String?,
        language: String,
        region: String?
    ): List<PlaceDto.Simple> {

        val responseMono: Mono<GoogleDto.GoogleApiResponse> = webClient.post()
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
            .bodyToMono(GoogleDto.GoogleApiResponse::class.java) // 응답 Body를 Mono<GoogleApiResponse>로 변환
            .doOnError { e -> logger.error("Error calling Google Places API", e) } // 에러 발생 시 로그 기록
            .onErrorReturn(GoogleDto.GoogleApiResponse(null)) // 에러 발생 시 기본값 반환

        // 비동기 작업이 끝날 때까지 기다렸다가 결과를 동기적으로 추출
        val googleResponse = responseMono.block()


        return googleResponse?.places?.map {
            PlaceDto.Simple.from(it)
        } ?: emptyList()
    }
}
