package io.github.constraintnative.generative

import io.github.constraintnative.core.SolutionId

public data class RankableSolution(
    val id: SolutionId,
    val summary: String
)

public data class RankingRequest(
    val solutions: List<RankableSolution>,
    val contextSummary: String
)

public data class RankedSolution(
    val id: SolutionId,
    val explanation: String? = null
)

public data class RankingResponse(
    val rankedSolutions: List<RankedSolution>
)
