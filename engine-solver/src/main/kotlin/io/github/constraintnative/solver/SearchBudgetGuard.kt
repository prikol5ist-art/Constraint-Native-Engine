package io.github.constraintnative.solver

import io.github.constraintnative.core.SearchBudget
import io.github.constraintnative.core.SearchTermination

internal class SearchBudgetGuard(
    private val budget: SearchBudget,
    private val startedAtMillis: Long,
    private val nowMillis: () -> Long
) {
    var expandedNodes: Long = 0
        private set

    fun checkTime(): SearchTermination? =
        if (nowMillis() - startedAtMillis >= budget.maxDurationMillis) {
            SearchTermination.TIME_BUDGET_EXHAUSTED
        } else {
            null
        }

    fun beforeExpansion(): SearchTermination? {
        if (expandedNodes >= budget.maxExpandedNodes) return SearchTermination.NODE_BUDGET_EXHAUSTED
        checkTime()?.let { return it }
        expandedNodes += 1
        return null
    }
}
