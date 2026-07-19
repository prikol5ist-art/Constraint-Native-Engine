package io.github.constraintnative.solver

import io.github.constraintnative.core.FeasibleSolution

internal class TopKCollector<A>(private val capacity: Int) {
    private val solutions = mutableMapOf<String, FeasibleSolution<A>>()

    fun add(solution: FeasibleSolution<A>) {
        solutions[solution.id.value] = solution
        if (solutions.size > capacity) {
            solutions.remove(ordered().last().id.value)
        }
    }

    fun ordered(): List<FeasibleSolution<A>> = solutions.values.sortedWith(
        compareByDescending<FeasibleSolution<A>> { it.totalScore }
            .thenBy { it.id.value }
    )

    fun isEmpty(): Boolean = solutions.isEmpty()
}
