package com.tribe.tribe_api.itinerary.service

import com.tribe.tribe_api.itinerary.dto.GoogleDto
import com.tribe.tribe_api.itinerary.dto.PlaceDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException

@Service
class GoogleMapService(
    restTemplateBuilder: RestTemplateBuilder,
    @Value("\${google.key}") private val apiKey: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val restTemplate = restTemplateBuilder.build()

    // 💡 반환 타입을 List<PlaceDto.Simple>로 변경
    fun searchPlaces(
        query: String?,
        language: String,
        region: String?
    ): List<PlaceDto.Simple> {

        // 1. 엔드포인트 URL 변경
        val url = "https://places.googleapis.com/v1/places:searchText"

        // 2. HTTP 헤더 설정
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Goog-Api-Key", apiKey)
            set("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location")
        }

        // 3. HTTP Body 설정
        val body = mapOf(
            "textQuery" to query,
            "languageCode" to language,
            "regionCode" to region
        )

        // 4. HttpEntity로 헤더와 Body를 합침
        val requestEntity = HttpEntity(body, headers)

        val googleResponse: GoogleDto.GoogleApiResponse? = try {
            // 5. POST 요청으로 변경 (exchange 사용)
            restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                GoogleDto.GoogleApiResponse::class.java
            ).body
        } catch (e: HttpClientErrorException) {
            logger.error("Error calling Google Places API: ${e.statusCode} ${e.responseBodyAsString}", e)
            null
        }

        // 6. 결과를 PlaceDto.Simple 리스트로 가공
        return googleResponse?.places?.map { placeResult ->
            PlaceDto.Simple.from(placeResult)
        } ?: emptyList()
    }
}