package io.github.constraintnative.gemini

import io.github.constraintnative.generative.RankingRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class PromptDataPayload(
    val context: String,
    val candidates: List<PromptCandidate>
)

@Serializable
private data class PromptCandidate(
    val id: String,
    val summary: String
)

public object GeminiPromptFactory {
    public fun createPrompt(request: RankingRequest): String {
        val payload = PromptDataPayload(
            context = request.contextSummary,
            candidates = request.solutions.map { PromptCandidate(it.id.value, it.summary) }
        )
        val payloadJson = Json.encodeToString(payload)

        return """
            You are a subjective ranking assistant. Your task is to rank the proposed solution candidates based on compatibility and the provided context.
            
            Rank the candidates from best to worst. For each candidate, provide a short, single-line explanation of why it is suitable or not (at most 200 characters, no control characters).
            
            You must return a valid JSON object matching this schema:
            {
              "ranked": [
                {
                  "solution_id": "string",
                  "explanation": "string"
                }
              ]
            }
            
            Ensure you include every solution ID from the input candidates exactly once. Do not invent any new solution IDs.
            
            Input Data:
            $payloadJson
        """.trimIndent()
    }
}
