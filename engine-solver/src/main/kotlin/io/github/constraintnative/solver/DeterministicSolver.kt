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

        fun isStateComplete(state: SearchState<A>): Boolean {
            return request.slots.all { req ->
                val count = state.slotCounts[req.slot] ?: 0
                count >= req.min
            }
        }

        fun evaluateCompleteAndSave(state: SearchState<A>): Boolean {
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
                    return true
                }
            }
            return false
        }

        if (request.searchMode == SearchMode.EXACT) {
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
                    evaluateCompleteAndSave(state)
                    return
                }

                val slotReq = request.slots[state.slotIndex]
                val currentCount = state.slotCounts[slotReq.slot] ?: 0

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
            // BOUNDED_BEAM SEARCH
            var currentBeam = listOf(
                SearchState<A>(
                    slotIndex = 0,
                    assignments = emptyList(),
                    selectedIds = emptySet(),
                    lastCandidateIndex = -1,
                    slotCounts = emptyMap()
                )
            )

            // If initial empty state is already complete (unlikely but possible if all slot mins are 0)
            if (isStateComplete(currentBeam[0])) {
                evaluateCompleteAndSave(currentBeam[0])
            }

            var levelExhausted = false
            while (currentBeam.isNotEmpty() && solutions.size < request.desiredSolutions && !levelExhausted) {
                val nextBeamCandidates = mutableListOf<SearchState<A>>()

                for (state in currentBeam) {
                    if (expandedNodes >= request.searchBudget.maxExpandedNodes) {
                        termination = SearchTermination.NODE_BUDGET_EXHAUSTED
                        trace.add(TraceEvent.SearchStopped(sequence++, "Node budget of ${request.searchBudget.maxExpandedNodes} exhausted"))
                        levelExhausted = true
                        break
                    }

                    val elapsed = clock.markNow() - startTime
                    if (elapsed >= request.searchBudget.maxDurationMillis) {
                        termination = SearchTermination.TIME_BUDGET_EXHAUSTED
                        trace.add(TraceEvent.SearchStopped(sequence++, "Time budget of ${request.searchBudget.maxDurationMillis}ms exhausted"))
                        levelExhausted = true
                        break
                    }

                    expandedNodes++

                    // Expand state: transition to target slots and assign a candidate
                    for (targetSlotIndex in state.slotIndex until request.slots.size) {
                        // Check if we can transition to targetSlotIndex (all intermediate slots must meet minimum requirement)
                        var canTransition = true
                        for (j in state.slotIndex until targetSlotIndex) {
                            val intermediateReq = request.slots[j]
                            val count = state.slotCounts[intermediateReq.slot] ?: 0
                            if (count < intermediateReq.min) {
                                canTransition = false
                                break
                            }
                        }

                        if (!canTransition) continue

                        val slotReq = request.slots[targetSlotIndex]
                        val currentCount = state.slotCounts[slotReq.slot] ?: 0

                        if (currentCount < slotReq.max) {
                            val startIndex = if (targetSlotIndex == state.slotIndex) state.lastCandidateIndex + 1 else 0
                            for (i in startIndex until request.candidates.size) {
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
                                        val nextState = SearchState(
                                            slotIndex = targetSlotIndex,
                                            assignments = newAssignments,
                                            selectedIds = state.selectedIds + candidate.id,
                                            lastCandidateIndex = i,
                                            slotCounts = newCounts
                                        )
                                        nextBeamCandidates.add(nextState)
                                    }
                                }
                            }
                        }
                    }
                }

                if (levelExhausted) break

                // Evaluate complete solutions in the next level candidates
                val completeInLevel = nextBeamCandidates.filter { isStateComplete(it) }
                for (state in completeInLevel) {
                    if (evaluateCompleteAndSave(state)) {
                        break
                    }
                }

                if (solutions.size >= request.desiredSolutions) {
                    break
                }

                if (nextBeamCandidates.isEmpty()) {
                    levelExhausted = true
                } else {
                    // Sort next beam candidates by provisional score descending, then by SolutionId lexicographically to break ties
                    val sortedCandidates = nextBeamCandidates.sortedWith(
                        compareByDescending<SearchState<A>> { state ->
                            val partialAssembly = PartialAssembly(state.assignments, complete = false)
                            request.softScorers.sumOf { scorer ->
                                scorer.score(partialAssembly, request.context).value
                            }
                        }.thenBy { state ->
                            SolutionCanonicalizer.canonicalize(state.assignments).value
                        }
                    )
                    currentBeam = sortedCandidates.take(request.searchBudget.beamWidth)
                }
            }
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
