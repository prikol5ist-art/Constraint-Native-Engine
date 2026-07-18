package io.github.constraintnative.generative

import io.github.constraintnative.core.FeasibleSolution
import io.github.constraintnative.core.SearchTermination

public enum class RankerStatus {
    NOT_CONFIGURED,
    APPLIED,
    FELL_BACK
}

public data class EngineResult<A>(
    val solutions: List<FeasibleSolution<A>>,
    val termination: SearchTermination,
    val rankerStatus: RankerStatus,
    val rankerError: String? = null
)
