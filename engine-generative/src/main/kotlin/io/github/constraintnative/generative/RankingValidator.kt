package io.github.constraintnative.generative

import io.github.constraintnative.core.FeasibleSolution
import io.github.constraintnative.core.SolutionId

public sealed interface ValidationResult {
    public data class Valid(
        val rankedSolutions: List<RankedSolution>
    ) : ValidationResult {
        public fun <A> reorder(original: List<FeasibleSolution<A>>): List<FeasibleSolution<A>> {
            val originalMap = original.associateBy { it.id }
            val reordered = mutableListOf<FeasibleSolution<A>>()
            val addedIds = mutableSetOf<SolutionId>()

            for (ranked in rankedSolutions) {
                val orig = originalMap[ranked.id]
                if (orig != null && ranked.id !in addedIds) {
                    reordered.add(orig)
                    addedIds.add(ranked.id)
                }
            }

            for (orig in original) {
                if (orig.id !in addedIds) {
                    reordered.add(orig)
                    addedIds.add(orig.id)
                }
            }

            return reordered
        }
    }

    public data class Invalid(
        val code: String,
        val message: String
    ) : ValidationResult
}

public object RankingValidator {
    public fun <A> validate(
        response: RankingResponse,
        knownSolutions: List<FeasibleSolution<A>>
    ): ValidationResult {
        val ranked = response.rankedSolutions
        if (ranked.isEmpty()) {
            return ValidationResult.Invalid("empty_ranking", "Ranker response must contain at least one ranked solution")
        }

        val knownIds = knownSolutions.map { it.id }.toSet()
        val seenIds = mutableSetOf<SolutionId>()

        if (ranked.size > knownSolutions.size) {
            return ValidationResult.Invalid("excessive_results", "Ranker returned more solutions (${ranked.size}) than requested (${knownSolutions.size})")
        }

        for (rankedSol in ranked) {
            val id = rankedSol.id
            if (id !in knownIds) {
                return ValidationResult.Invalid("unknown_solution_id", "Solution ID '${id.value}' is not present in the requested solutions")
            }
            if (id in seenIds) {
                return ValidationResult.Invalid("duplicate_solution_id", "Solution ID '${id.value}' is duplicated in ranker response")
            }
            seenIds.add(id)

            val expl = rankedSol.explanation
            if (expl != null) {
                if (expl.length > 2000) {
                    return ValidationResult.Invalid("oversized_explanation", "Explanation for solution '${id.value}' exceeds 2000 characters")
                }
                if (expl.any { it.isISOControl() }) {
                    return ValidationResult.Invalid("invalid_explanation_characters", "Explanation for solution '${id.value}' contains control characters")
                }
            }
        }

        return ValidationResult.Valid(ranked)
    }
}
