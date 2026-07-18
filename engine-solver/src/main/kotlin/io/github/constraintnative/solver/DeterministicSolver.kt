package io.github.constraintnative.solver

import io.github.constraintnative.core.*

public class DeterministicSolver(
    private val clock: MonotonicClock = SystemClock
) : ConstraintSolver {

    public fun interface MonotonicClock {
        public fun markNow(): Long
    }

    public object SystemClock : MonotonicClock {
        override fun markNow(): Long = System.nanoTime() / 1_000_000
    }

    override fun <A, C> solve(request: SolveRequest<A, C>): SolveResult<A> {
        val startTime = clock.markNow()
        val solutions = mutableListOf<FeasibleSolution<A>>()
        val trace = mutableListOf<TraceEvent>()
        var expandedNodes = 0L
        var sequence = 0L
        var termination = SearchTermination.INFEASIBLE

        fun dfs(state: SearchState<A>) {
            if (expandedNodes >= request.searchBudget.maxExpandedNodes) {
                termination = SearchTermination.NODE_BUDGET_EXHAUSTED
                trace.add(TraceEvent.SearchStopped(sequence++, "Node budget of ${request.searchBudget.maxExpandedNodes} exhausted"))
                return
            }

            val elapsed = clock.markNow() - startTime
            if (elapsed >= request.searchBudget.maxDurationMillis) {
                termination = SearchTermination.TIME_BUDGET_EXHAUSTED
                trace.add(TraceEvent.SearchStopped(sequence++, "Time budget of ${request.searchBudget.maxDurationMillis}ms exhausted"))
                return
            }

            if (solutions.size >= request.desiredSolutions) {
                termination = SearchTermination.SOLUTION_LIMIT_REACHED
                return
            }

            expandedNodes++

            if (state.slotIndex >= request.slots.size) {
                val partialAssembly = PartialAssembly(state.assignments, complete = true)
                var allSatisfied = true
                for (constraint in request.hardConstraints) {
                    val verdict = constraint.evaluate(partialAssembly, request.context)
                    if (verdict is ConstraintVerdict.Violated) {
                        allSatisfied = false
                        trace.add(TraceEvent.PartialAssemblyPruned(sequence++, SlotId("complete"), verdict.message))
                        break
                    }
                }

                if (allSatisfied) {
                    val scoreComponents = request.softScorers.map { scorer ->
                        scorer.score(partialAssembly, request.context)
                    }
                    val totalScore = scoreComponents.sumOf { it.value }
                    val solutionId = SolutionCanonicalizer.canonicalize(state.assignments)

                    val solution = FeasibleSolution(
                        id = solutionId,
                        assignments = state.assignments,
                        scoreComponents = scoreComponents,
                        totalScore = totalScore
                    )
                    solutions.add(solution)
                    trace.add(TraceEvent.SolutionAccepted(sequence++, solutionId, totalScore))

                    if (solutions.size >= request.desiredSolutions) {
                        termination = SearchTermination.SOLUTION_LIMIT_REACHED
                    }
                }
                return
            }

            val slotReq = request.slots[state.slotIndex]
            val currentCount = state.slotCounts[slotReq.slot] ?: 0

            // Option 1: Assign a candidate to the current slot if slot capacity allows
            if (currentCount < slotReq.max) {
                for (i in (state.lastCandidateIndex + 1) until request.candidates.size) {
                    if (termination != SearchTermination.INFEASIBLE && termination != SearchTermination.EXHAUSTIVE) {
                        if (solutions.size >= request.desiredSolutions) return
                        if (expandedNodes >= request.searchBudget.maxExpandedNodes) return
                        if (clock.markNow() - startTime >= request.searchBudget.maxDurationMillis) return
                    }

                    val candidate = request.candidates[i]
                    if (candidate.id !in state.selectedIds && slotReq.slot in candidate.eligibleSlots) {
                        val assignment = Assignment(slotReq.slot, candidate)
                        val newAssignments = state.assignments + assignment
                        val partialAssembly = PartialAssembly(newAssignments, complete = false)

                        var checkPassed = true
                        for (constraint in request.hardConstraints) {
                            val verdict = constraint.evaluate(partialAssembly, request.context)
                            if (verdict is ConstraintVerdict.Violated) {
                                checkPassed = false
                                trace.add(TraceEvent.CandidateRejected(sequence++, candidate.id, slotReq.slot, verdict.message))
                                break
                            }
                        }

                        if (checkPassed) {
                            val newCounts = state.slotCounts.toMutableMap().apply {
                                put(slotReq.slot, currentCount + 1)
                            }
                            dfs(
                                SearchState(
                                    slotIndex = state.slotIndex,
                                    assignments = newAssignments,
                                    selectedIds = state.selectedIds + candidate.id,
                                    lastCandidateIndex = i,
                                    slotCounts = newCounts
                                )
                            )
                        }
                    }
                }
            }

            // Option 2: Transition to the next slot if slot minimum capacity is satisfied
            if (currentCount >= slotReq.min) {
                if (solutions.size >= request.desiredSolutions) return
                if (expandedNodes >= request.searchBudget.maxExpandedNodes) return
                if (clock.markNow() - startTime >= request.searchBudget.maxDurationMillis) return

                dfs(
                    SearchState(
                        slotIndex = state.slotIndex + 1,
                        assignments = state.assignments,
                        selectedIds = state.selectedIds,
                        lastCandidateIndex = -1,
                        slotCounts = state.slotCounts
                    )
                )
            }
        }

        if (request.searchMode == SearchMode.EXACT) {
            dfs(
                SearchState(
                    slotIndex = 0,
                    assignments = emptyList(),
                    selectedIds = emptySet(),
                    lastCandidateIndex = -1,
                    slotCounts = emptyMap()
                )
            )
        } else {
            // Placeholder for Bounded Beam Search
            termination = SearchTermination.EXHAUSTIVE
        }

        if (termination == SearchTermination.INFEASIBLE) {
            termination = if (solutions.isNotEmpty()) {
                SearchTermination.EXHAUSTIVE
            } else {
                SearchTermination.INFEASIBLE
            }
        }

        val sortedSolutions = solutions.distinctBy { it.id }.sortedWith(
            compareByDescending<FeasibleSolution<A>> { it.totalScore }
                .thenBy { it.id.value }
        )

        return SolveResult(
            solutions = sortedSolutions,
            termination = termination,
            expandedNodes = expandedNodes,
            trace = trace
        )
    }
}
