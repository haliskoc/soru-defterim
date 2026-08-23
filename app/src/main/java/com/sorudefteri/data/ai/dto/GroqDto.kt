package com.sorudefteri.data.ai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Groq OpenAI uyumlu Chat Completions API istek gövdesi (POST https://api.groq.com/openai/v1/chat/completions)
 */
@Serializable
data class GroqChatRequest(
    val model: String,
    val messages: List<GroqMessage>,
    val temperature: Double = 0.2
)

@Serializable
data class GroqMessage(
    val role: String,
    val content: List<GroqContentPart>
)

@Serializable
data class GroqContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url")
    val image_url: GroqImageUrl? = null
)

@Serializable
data class GroqImageUrl(
    val url: String
)

/**
 * Groq Chat Completions API yanıt gövdesi
 */
@Serializable
data class GroqChatResponse(
    val choices: List<GroqChoice>? = null,
    val error: GroqError? = null
)

@Serializable
data class GroqChoice(
    val message: GroqResponseMessage? = null
)

@Serializable
data class GroqResponseMessage(
    val content: String? = null
)

@Serializable
data class GroqError(
    val message: String? = null,
    val type: String? = null
)

/**
 * Groq Models API yanıt gövdesi (GET https://api.groq.com/openai/v1/models)
 */
@Serializable
data class GroqModelListResponse(
    val `data`: List<GroqModelItem>? = null,
    val error: GroqError? = null
)

@Serializable
data class GroqModelItem(
    val id: String,
    @SerialName("owned_by")
    val ownedBy: String? = null,
    val active: Boolean? = true
)

