package io.github.constraintnative.generative

import io.github.constraintnative.core.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RankingValidatorTest {

    private val sol1 = FeasibleSolution<String>(
        id = SolutionId("sol1"),
        assignments = emptyList(),
        scoreComponents = emptyList(),
        totalScore = 10.0
    )
    private val sol2 = FeasibleSolution<String>(
        id = SolutionId("sol2"),
        assignments = emptyList(),
        scoreComponents = emptyList(),
        totalScore = 9.0
    )
    private val sol3 = FeasibleSolution<String>(
        id = SolutionId("sol3"),
        assignments = emptyList(),
        scoreComponents = emptyList(),
        totalScore = 8.0
    )

    private val knownSolutions = listOf(sol1, sol2, sol3)

    @Test
    fun `should accept valid complete ranking`() {
        val response = RankingResponse(
            rankedSolutions = listOf(
                RankedSolution(SolutionId("sol2"), "better"),
                RankedSolution(SolutionId("sol1"), "best"),
                RankedSolution(SolutionId("sol3"), "good")
            )
        )

        val outcome = RankingValidator.validate(response, knownSolutions)

        assertThat(outcome).isInstanceOf(ValidationResult.Valid::class.java)
        
        val reordered = (outcome as ValidationResult.Valid).reorder(knownSolutions)
        assertThat(reordered).containsExactly(sol2, sol1, sol3)
    }

    @Test
    fun `should accept valid subset ranking and append omitted solutions`() {
        val response = RankingResponse(
            rankedSolutions = listOf(
                RankedSolution(SolutionId("sol2"), "better")
            )
        )

        val outcome = RankingValidator.validate(response, knownSolutions)

        assertThat(outcome).isInstanceOf(ValidationResult.Valid::class.java)

        val reordered = (outcome as ValidationResult.Valid).reorder(knownSolutions)
        // sol2 is ranked first, then omitted ones (sol1, sol3) are appended in their original order
        assertThat(reordered).containsExactly(sol2, sol1, sol3)
    }

    @Test
    fun `should reject empty ranking`() {
        val response = RankingResponse(emptyList())
        val outcome = RankingValidator.validate(response, knownSolutions)
        assertThat(outcome).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((outcome as ValidationResult.Invalid).code).isEqualTo("empty_ranking")
    }

    @Test
    fun `should reject unknown IDs`() {
        val response = RankingResponse(
            rankedSolutions = listOf(
                RankedSolution(SolutionId("sol2")),
                RankedSolution(SolutionId("unknown_id"))
            )
        )
        val outcome = RankingValidator.validate(response, knownSolutions)
        assertThat(outcome).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((outcome as ValidationResult.Invalid).code).isEqualTo("unknown_solution_id")
    }

    @Test
    fun `should reject duplicate IDs`() {
        val response = RankingResponse(
            rankedSolutions = listOf(
                RankedSolution(SolutionId("sol2")),
                RankedSolution(SolutionId("sol2"))
            )
        )
        val outcome = RankingValidator.validate(response, knownSolutions)
        assertThat(outcome).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((outcome as ValidationResult.Invalid).code).isEqualTo("duplicate_solution_id")
    }

    @Test
    fun `should reject excessive result counts`() {
        val response = RankingResponse(
            rankedSolutions = listOf(
                RankedSolution(SolutionId("sol1")),
                RankedSolution(SolutionId("sol2")),
                RankedSolution(SolutionId("sol3")),
                RankedSolution(SolutionId("sol3")) // duplicate makes it size 4 > 3
            )
        )
        // Since we already check duplicates, let's just make it have more items than known
        val outcome = RankingValidator.validate(
            RankingResponse(listOf(RankedSolution(SolutionId("sol1")), RankedSolution(SolutionId("sol2")), RankedSolution(SolutionId("sol3")), RankedSolution(SolutionId("sol1")))),
            knownSolutions
        )
        assertThat(outcome).isInstanceOf(ValidationResult.Invalid::class.java)
    }

    @Test
    fun `should reject oversized explanations`() {
        val longExpl = "a".repeat(2001)
        val response = RankingResponse(
            rankedSolutions = listOf(RankedSolution(SolutionId("sol1"), longExpl))
        )
        val outcome = RankingValidator.validate(response, knownSolutions)
        assertThat(outcome).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((outcome as ValidationResult.Invalid).code).isEqualTo("oversized_explanation")
    }

    @Test
    fun `should reject control characters in explanation`() {
        val response = RankingResponse(
            rankedSolutions = listOf(RankedSolution(SolutionId("sol1"), "invalid\u0000explanation"))
        )
        val outcome = RankingValidator.validate(response, knownSolutions)
        assertThat(outcome).isInstanceOf(ValidationResult.Invalid::class.java)
        assertThat((outcome as ValidationResult.Invalid).code).isEqualTo("invalid_explanation_characters")
    }
}
