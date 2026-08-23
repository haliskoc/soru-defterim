package com.sorudefteri.data.ai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gemini GenerateContent API istek gövdesi (POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key})
 */
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart> = emptyList()
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data")
    val inline_data: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    @SerialName("mime_type")
    val mime_type: String,
    val data: String
)

/**
 * Gemini GenerateContent API yanıt gövdesi
 */
@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@Serializable
data class GeminiError(
    val code: Int? = null,
    val message: String? = null
)

/**
 * Gemini ListModels API yanıt gövdesi (GET https://generativelanguage.googleapis.com/v1beta/models?key={key})
 */
@Serializable
data class GeminiModelListResponse(
    val models: List<GeminiModelItem>? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiModelItem(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val supportedGenerationMethods: List<String> = emptyList()
)
