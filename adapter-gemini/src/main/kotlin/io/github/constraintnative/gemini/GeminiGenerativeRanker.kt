package io.github.constraintnative.gemini

import io.github.constraintnative.core.SolutionId
import io.github.constraintnative.generative.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

public class GeminiGenerativeRanker(
    private val transport: GeminiTransport
) : GenerativeRanker {

    private companion object {
        private const val MAX_TRANSPORT_RESPONSE_CHARS = 256 * 1024
        private const val MAX_RANKING_JSON_CHARS = 64 * 1024
    }

    private val envelopeJson = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    private val rankingJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    private val strictJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    override suspend fun rank(request: RankingRequest): RankingResponse {
        val prompt = GeminiPromptFactory.createPrompt(request)

        val geminiRequest = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt)
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = GeminiSchema(
                    type = "OBJECT",
                    properties = mapOf(
                        "ranked" to GeminiSchema(
                            type = "ARRAY",
                            items = GeminiSchema(
                                type = "OBJECT",
                                properties = mapOf(
                                    "solution_id" to GeminiSchema(type = "STRING"),
                                    "explanation" to GeminiSchema(type = "STRING")
                                ),
                                required = listOf("solution_id", "explanation")
                            )
                        )
                    ),
                    required = listOf("ranked")
                )
            )
        )

        val requestJson = strictJson.encodeToString(geminiRequest)
        val responseJson = transport.generateContent(requestJson)

        require(responseJson.length <= MAX_TRANSPORT_RESPONSE_CHARS) {
            "Gemini transport response exceeds $MAX_TRANSPORT_RESPONSE_CHARS characters"
        }

        val geminiResponse = envelopeJson.decodeFromString<GeminiGenerateResponse>(responseJson)
        val candidateText = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalArgumentException("Empty response from Gemini model")

        require(candidateText.length <= MAX_RANKING_JSON_CHARS) {
            "Gemini ranking JSON exceeds $MAX_RANKING_JSON_CHARS characters"
        }

        val rankingDto = rankingJson.decodeFromString<GeminiRankingResponseDto>(candidateText)

        return RankingResponse(
            rankedSolutions = rankingDto.ranked.map { dto ->
                RankedSolution(
                    id = SolutionId(dto.solutionId),
                    explanation = dto.explanation
                )
            }
        )
    }
}
