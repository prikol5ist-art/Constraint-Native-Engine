package io.github.constraintnative.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
public data class GeminiPart(
    val text: String
)

@Serializable
public data class GeminiSchema(
    val type: String,
    val properties: Map<String, GeminiSchema>? = null,
    val required: List<String>? = null,
    val items: GeminiSchema? = null
)

@Serializable
public data class GeminiGenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: GeminiSchema? = null
)

@Serializable
public data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
public data class GeminiGenerateResponse(
    val candidates: List<GeminiResponseCandidate>
)

@Serializable
public data class GeminiResponseCandidate(
    val content: GeminiContent
)

@Serializable
public data class GeminiRankedSolutionDto(
    @SerialName("solution_id") val solutionId: String,
    val explanation: String? = null
)

@Serializable
public data class GeminiRankingResponseDto(
    val ranked: List<GeminiRankedSolutionDto>
)
