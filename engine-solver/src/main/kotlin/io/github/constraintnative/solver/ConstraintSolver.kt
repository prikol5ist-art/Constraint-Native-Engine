package io.github.constraintnative.solver

import io.github.constraintnative.core.SolveRequest
import io.github.constraintnative.core.SolveResult

public interface ConstraintSolver {
    public fun <A, C> solve(request: SolveRequest<A, C>): SolveResult<A>
}
