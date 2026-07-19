package io.github.constraintnative.gemini

import io.github.constraintnative.core.SolutionId
import io.github.constraintnative.generative.RankableSolution
import io.github.constraintnative.generative.RankingRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiGenerativeRankerTest {

    private val rankRequest = RankingRequest(
        solutions = listOf(
            RankableSolution(SolutionId("sol1"), "Outfit 1"),
            RankableSolution(SolutionId("sol2"), "Outfit 2")
        ),
        contextSummary = "rainy weather"
    )

    @Test
    fun `should serialize request correctly and parse successful response`() = runTest {
        val mockTransport = GeminiTransport { requestJson ->
            // Check that the requestJson is valid GeminiGenerateRequest JSON
            assert(requestJson.contains("rainy weather"))
            assert(requestJson.contains("sol1"))
            assert(requestJson.contains("Outfit 1"))

            // Return mock successful response
            """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{ \"ranked\": [ { \"solution_id\": \"sol2\", \"explanation\": \"Good\" }, { \"solution_id\": \"sol1\", \"explanation\": \"Okay\" } ] }"
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent()
        }

        val ranker = GeminiGenerativeRanker(mockTransport)
        val response = ranker.rank(rankRequest)

        assertThat(response.rankedSolutions).hasSize(2)
        assertThat(response.rankedSolutions[0].id.value).isEqualTo("sol2")
        assertThat(response.rankedSolutions[0].explanation).isEqualTo("Good")
        assertThat(response.rankedSolutions[1].id.value).isEqualTo("sol1")
        assertThat(response.rankedSolutions[1].explanation).isEqualTo("Okay")
    }

    @Test
    fun `should reject response with unknown keys under strict json`() = runTest {
        val mockTransport = GeminiTransport {
            """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{ \"ranked\": [ { \"solution_id\": \"sol2\", \"explanation\": \"Good\", \"unexpected_key\": 123 } ] }"
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent()
        }

        val ranker = GeminiGenerativeRanker(mockTransport)
        assertThatThrownBy { runTest { ranker.rank(rankRequest) } }
            .isInstanceOf(Exception::class.java) // Strict kotlinx.serialization should throw
    }

    @Test
    fun `should propagate transport exception`() = runTest {
        val mockTransport = GeminiTransport {
            throw RuntimeException("Network Error")
        }

        val ranker = GeminiGenerativeRanker(mockTransport)
        try {
            ranker.rank(rankRequest)
            assert(false) { "Expected transport exception" }
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("Network Error")
        }
    }

    @Test
    fun `should propagate cancellation exception`() = runTest {
        val mockTransport = GeminiTransport {
            throw CancellationException("Cancelled")
        }

        val ranker = GeminiGenerativeRanker(mockTransport)
        try {
            ranker.rank(rankRequest)
            assert(false) { "Expected CancellationException" }
        } catch (e: CancellationException) {
            assertThat(e.message).isEqualTo("Cancelled")
        }
    }

    @Test
    fun `should parse successful response with additional real Gemini envelope fields`() = runTest {
        val mockTransport = GeminiTransport {
            """
            {
              "candidates": [{
                "content": {"role":"model","parts":[{"text":"{\"ranked\":[{\"solution_id\":\"sol1\",\"explanation\":\"Good\"}]}"}]},
                "finishReason": "STOP",
                "index": 0
              }],
              "modelVersion": "gemini-1.5-flash",
              "usageMetadata": {"promptTokenCount": 12}
            }
            """.trimIndent()
        }

        val ranker = GeminiGenerativeRanker(mockTransport)
        val response = ranker.rank(rankRequest)

        assertThat(response.rankedSolutions).hasSize(1)
        assertThat(response.rankedSolutions[0].id.value).isEqualTo("sol1")
        assertThat(response.rankedSolutions[0].explanation).isEqualTo("Good")
    }

    @Test
    fun `should reject oversized transport response`() = runTest {
        val oversizedResponse = "x".repeat(256 * 1024 + 1)
        val mockTransport = GeminiTransport { oversizedResponse }
        val ranker = GeminiGenerativeRanker(mockTransport)

        var thrown: Throwable? = null
        try {
            ranker.rank(rankRequest)
        } catch (t: Throwable) {
            thrown = t
        }
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Gemini transport response exceeds")
    }

    @Test
    fun `should reject oversized extracted candidate text`() = runTest {
        val oversizedCandidateText = "{\"ranked\":[], \"dummy\":\"" + "x".repeat(64 * 1024 + 1) + "\"}"
        val escapedCandidateText = oversizedCandidateText.replace("\"", "\\\"")
        val mockTransport = GeminiTransport {
            """
            {
              "candidates": [{
                "content": {"parts":[{"text":"$escapedCandidateText"}]}
              }]
            }
            """.trimIndent()
        }
        val ranker = GeminiGenerativeRanker(mockTransport)

        var thrown: Throwable? = null
        try {
            ranker.rank(rankRequest)
        } catch (t: Throwable) {
            thrown = t
        }
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Gemini ranking JSON exceeds")
    }
}
