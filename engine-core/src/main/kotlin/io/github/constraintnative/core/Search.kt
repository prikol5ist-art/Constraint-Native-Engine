package io.github.constraintnative.core

import kotlinx.serialization.Serializable

@Serializable
public data class SearchBudget(
    val maxExpandedNodes: Long,
    val maxDurationMillis: Long,
    val beamWidth: Int
) {
    init {
        require(maxExpandedNodes > 0) { "maxExpandedNodes must be positive" }
        require(maxDurationMillis > 0) { "maxDurationMillis must be positive" }
        require(beamWidth > 0) { "beamWidth must be positive" }
    }
}

public enum class SearchMode {
    EXACT,
    BOUNDED_BEAM
}

public data class SolveRequest<A, C>(
    val candidates: List<Candidate<A>>,
    val slots: List<SlotRequirement>,
    val context: C,
    val hardConstraints: List<HardConstraint<A, C>>,
    val softScorers: List<SoftScorer<A, C>>,
    val desiredSolutions: Int,
    val searchMode: SearchMode,
    val searchBudget: SearchBudget
) {
    init {
        require(desiredSolutions in 1..100) {
            "desiredSolutions must be in 1..100, got: $desiredSolutions"
        }
        require(searchBudget.beamWidth >= desiredSolutions) {
            "beamWidth (${searchBudget.beamWidth}) must be >= desiredSolutions ($desiredSolutions)"
        }
        require(candidates.distinctBy { it.id }.size == candidates.size) {
            "Candidate IDs must be unique"
        }
        require(slots.distinctBy { it.slot }.size == slots.size) {
            "Slot requirements must be unique"
        }
    }
}

public data class FeasibleSolution<A>(
    val id: SolutionId,
    val assignments: List<Assignment<A>>,
    val scoreComponents: List<ScoreComponent>,
    val totalScore: Double,
    val explanation: String? = null
)

public enum class SearchTermination {
    EXHAUSTIVE,
    BOUNDED_SEARCH_COMPLETED,
    NODE_BUDGET_EXHAUSTED,
    TIME_BUDGET_EXHAUSTED,
    INFEASIBLE
}

public data class SolveResult<A>(
    val solutions: List<FeasibleSolution<A>>,
    val termination: SearchTermination,
    val expandedNodes: Long,
    val trace: List<TraceEvent>
)
