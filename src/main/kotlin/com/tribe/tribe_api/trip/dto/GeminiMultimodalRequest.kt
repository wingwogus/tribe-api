package com.tribe.tribe_api.trip.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class GeminiMultimodalRequest(
    val contents: List<Content>
) {
    data class Content(
        val parts: List<Part>
    )

    data class Part(
        val text: String? = null,
        @JsonProperty("inline_data") val inlineData: InlineData? = null
    )

    data class InlineData(
        @JsonProperty("mime_type") val mimeType: String,
        val data: String
    )
}