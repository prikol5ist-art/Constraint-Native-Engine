package io.github.constraintnative.gemini

import io.github.constraintnative.core.SolutionId
import io.github.constraintnative.generative.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

public class GeminiGenerativeRanker(
    private val transport: GeminiTransport
) : GenerativeRanker {

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

        val geminiResponse = strictJson.decodeFromString<GeminiGenerateResponse>(responseJson)
        val candidateText = geminiResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw RuntimeException("Empty response from Gemini model")

        val rankingDto = strictJson.decodeFromString<GeminiRankingResponseDto>(candidateText)

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
