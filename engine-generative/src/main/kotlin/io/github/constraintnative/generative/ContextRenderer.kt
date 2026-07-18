package io.github.constraintnative.generative

import io.github.constraintnative.core.FeasibleSolution

public interface ContextRenderer<A, C> {
    public fun renderContext(context: C): String
    public fun renderSolution(solution: FeasibleSolution<A>): String
}
