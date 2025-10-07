package com.tribe.tribe_api.trip.dto

data class GeminiRequest(
    val contents: List<Content>
) {
    constructor(prompt: String) : this(
        contents = listOf(
            Content(
                parts = listOf(
                    Part(text = prompt)
                )
            )
        )
    )

    data class Content(
        val parts: List<Part>
    )

    data class Part(
        val text: String
    )
}