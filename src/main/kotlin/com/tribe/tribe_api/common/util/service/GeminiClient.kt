package com.tribe.tribe_api.common.util.service

import com.tribe.tribe_api.trip.dto.GeminiMultimodalRequest
import com.tribe.tribe_api.trip.dto.GeminiRequest
import com.tribe.tribe_api.trip.dto.GeminiResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
class GeminiApiClient(
    private val webClient: WebClient,
    @Value("\${gemini.api.url}") private val apiUrl: String,
    @Value("\${gemini.api.key}") private val apiKey: String
) {

    fun getFeedback(prompt: String): String? {
        val request = GeminiRequest(prompt)
        val uri = "$apiUrl?key=$apiKey"

        return try {
            val response = webClient.post()
                .uri(uri)
                .body(Mono.just(request), GeminiRequest::class.java)
                .retrieve()
                .bodyToMono(GeminiResponse::class.java)
                .block()

            response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            throw RuntimeException("Gemini API 호출 중 오류가 발생했습니다.", e)
        }
    }

    fun generateContentFromImage(prompt: String, base64Image: String, mimeType: String): String? {
        val textPart = GeminiMultimodalRequest.Part(text = prompt)
        val imagePart = GeminiMultimodalRequest.Part(
            inlineData = GeminiMultimodalRequest.InlineData(mimeType = mimeType, data = base64Image)
        )

        val request = GeminiMultimodalRequest(
            contents = listOf(
                GeminiMultimodalRequest.Content(parts = listOf(textPart, imagePart))
            )
        )

        val uri = "$apiUrl?key=$apiKey"

        return try {
            val response = webClient.post()
                .uri(uri)
                .body(Mono.just(request), GeminiMultimodalRequest::class.java)
                .retrieve()
                .bodyToMono(GeminiResponse::class.java)
                .block()

            response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
        } catch (e: Exception) {
            println("===== Gemini API Error =====")
            e.printStackTrace()
            println("==========================")
            throw RuntimeException("Gemini API 호출 중 오류가 발생했습니다.", e)
        }
    }
}