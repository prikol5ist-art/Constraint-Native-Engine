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
        val trace = mutableListOf<TraceEvent>()

        return if (request.searchMode == SearchMode.EXACT) {
            solveExact(request, startTime, trace)
        } else {
            solveBeam(request, startTime, trace)
        }
    }

    private fun <A, C> solveExact(
        request: SolveRequest<A, C>,
        startTime: Long,
        trace: MutableList<TraceEvent>
    ): SolveResult<A> {
        val guard = SearchBudgetGuard(request.searchBudget, startTime) { clock.markNow() }
        var interruption: SearchTermination? = null
        var sequence = trace.size.toLong()
        val exactCollector = TopKCollector<A>(request.desiredSolutions)

        fun dfs(state: SearchState<A>) {
            if (interruption != null) return

            val budgetInterruption = guard.beforeExpansion()
            if (budgetInterruption != null) {
                interruption = budgetInterruption
                trace.add(
                    TraceEvent.SearchStopped(
                        sequence = sequence++,
                        reason = TraceEvent.sanitizeMessage(
                            when (budgetInterruption) {
                                SearchTermination.NODE_BUDGET_EXHAUSTED -> "Node budget of ${request.searchBudget.maxExpandedNodes} exhausted"
                                SearchTermination.TIME_BUDGET_EXHAUSTED -> "Time budget of ${request.searchBudget.maxDurationMillis}ms exhausted"
                                else -> "Budget exhausted"
                            }
                        )
                    )
                )
                return
            }

            if (state.slotIndex >= request.slots.size) {
                val partialAssembly = PartialAssembly(state.assignments, complete = true)
                var allSatisfied = true
                for (constraint in request.hardConstraints) {
                    val verdict = constraint.evaluate(partialAssembly, request.context)
                    if (verdict is ConstraintVerdict.Violated) {
                        allSatisfied = false
                        trace.add(
                            TraceEvent.PartialAssemblyPruned(
                                sequence = sequence++,
                                slotId = SlotId("complete"),
                                reason = TraceEvent.sanitizeMessage(verdict.message)
                            )
                        )
                        break
                    }
                }

                if (allSatisfied) {
                    val scoreComponents = request.softScorers.map { scorer ->
                        scorer.score(partialAssembly, request.context)
                    }
                    val totalScore = scoreComponents.sumOf { it.value }
                    require(totalScore.isFinite()) { "Total score must be finite" }
                    val solutionId = SolutionCanonicalizer.canonicalize(state.assignments)

                    val solution = FeasibleSolution(
                        id = solutionId,
                        assignments = state.assignments,
                        scoreComponents = scoreComponents,
                        totalScore = totalScore
                    )
                    exactCollector.add(solution)
                    trace.add(TraceEvent.SolutionAccepted(sequence++, solutionId, totalScore))
                }
                return
            }

            val slotReq = request.slots[state.slotIndex]
            val currentCount = state.slotCounts[slotReq.slot] ?: 0

            if (currentCount < slotReq.max) {
                for (i in (state.lastCandidateIndex + 1) until request.candidates.size) {
                    if (interruption != null) return

                    val timeInterruption = guard.checkTime()
                    if (timeInterruption != null) {
                        interruption = timeInterruption
                        trace.add(
                            TraceEvent.SearchStopped(
                                sequence = sequence++,
                                reason = TraceEvent.sanitizeMessage("Time budget of ${request.searchBudget.maxDurationMillis}ms exhausted")
                            )
                        )
                        return
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
                                trace.add(
                                    TraceEvent.CandidateRejected(
                                        sequence = sequence++,
                                        candidateId = candidate.id,
                                        slotId = slotReq.slot,
                                        reason = TraceEvent.sanitizeMessage(verdict.message)
                                    )
                                )
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
                if (interruption != null) return

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

        val termination = interruption ?: if (exactCollector.isEmpty()) {
            SearchTermination.INFEASIBLE
        } else {
            SearchTermination.EXHAUSTIVE
        }

        return SolveResult(
            solutions = exactCollector.ordered(),
            termination = termination,
            expandedNodes = guard.expandedNodes,
            trace = trace
        )
    }

    private fun <A, C> solveBeam(
        request: SolveRequest<A, C>,
        startTime: Long,
        trace: MutableList<TraceEvent>
    ): SolveResult<A> {
        val guard = SearchBudgetGuard(request.searchBudget, startTime) { clock.markNow() }
        var interruption: SearchTermination? = null
        var sequence = trace.size.toLong()
        val solutions = mutableListOf<FeasibleSolution<A>>()

        fun isStateComplete(state: SearchState<A>): Boolean {
            return request.slots.all { req ->
                val count = state.slotCounts[req.slot] ?: 0
                count >= req.min
            }
        }

        fun evaluateCompleteAndSave(state: SearchState<A>) {
            val partialAssembly = PartialAssembly(state.assignments, complete = true)
            var allSatisfied = true
            for (constraint in request.hardConstraints) {
                val verdict = constraint.evaluate(partialAssembly, request.context)
                if (verdict is ConstraintVerdict.Violated) {
                    allSatisfied = false
                    trace.add(
                        TraceEvent.PartialAssemblyPruned(
                            sequence++,
                            SlotId("complete"),
                            TraceEvent.sanitizeMessage(verdict.message)
                        )
                    )
                    break
                }
            }

            if (allSatisfied) {
                val scoreComponents = request.softScorers.map { scorer ->
                    scorer.score(partialAssembly, request.context)
                }
                val totalScore = scoreComponents.sumOf { it.value }
                require(totalScore.isFinite()) { "Total score must be finite" }
                val solutionId = SolutionCanonicalizer.canonicalize(state.assignments)

                val solution = FeasibleSolution(
                    id = solutionId,
                    assignments = state.assignments,
                    scoreComponents = scoreComponents,
                    totalScore = totalScore
                )
                solutions.add(solution)
                trace.add(TraceEvent.SolutionAccepted(sequence++, solutionId, totalScore))
            }
        }

        val stateComparator = compareByDescending<SearchState<A>> { state ->
            val partialAssembly = PartialAssembly(state.assignments, complete = false)
            request.softScorers.sumOf { scorer ->
                scorer.score(partialAssembly, request.context).value
            }
        }.thenBy { state ->
            SolutionCanonicalizer.canonicalize(state.assignments).value
        }

        var currentBeam = listOf(
            SearchState<A>(
                slotIndex = 0,
                assignments = emptyList(),
                selectedIds = emptySet(),
                lastCandidateIndex = -1,
                slotCounts = emptyMap()
            )
        )

        if (isStateComplete(currentBeam[0])) {
            evaluateCompleteAndSave(currentBeam[0])
        }

        var levelExhausted = false
        while (currentBeam.isNotEmpty() && !levelExhausted) {
            val nextBeamCandidates = mutableListOf<SearchState<A>>()

            for (state in currentBeam) {
                // Expand state: transition to target slots and assign a candidate
                for (targetSlotIndex in state.slotIndex until request.slots.size) {
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
                        val startIndex =
                            if (targetSlotIndex == state.slotIndex) state.lastCandidateIndex + 1 else 0
                        for (i in startIndex until request.candidates.size) {
                            val timeInterruption = guard.checkTime()
                            if (timeInterruption != null) {
                                interruption = timeInterruption
                                trace.add(
                                    TraceEvent.SearchStopped(
                                        sequence++,
                                        TraceEvent.sanitizeMessage("Time budget of ${request.searchBudget.maxDurationMillis}ms exhausted")
                                    )
                                )
                                levelExhausted = true
                                break
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
                                        trace.add(
                                            TraceEvent.CandidateRejected(
                                                sequence++,
                                                candidate.id,
                                                slotReq.slot,
                                                TraceEvent.sanitizeMessage(verdict.message)
                                            )
                                        )
                                        break
                                    }
                                }

                                if (checkPassed) {
                                    val budgetInterruption = guard.beforeExpansion()
                                    if (budgetInterruption != null) {
                                        interruption = budgetInterruption
                                        trace.add(
                                            TraceEvent.SearchStopped(
                                                sequence++,
                                                TraceEvent.sanitizeMessage(
                                                    when (budgetInterruption) {
                                                        SearchTermination.NODE_BUDGET_EXHAUSTED -> "Node budget of ${request.searchBudget.maxExpandedNodes} exhausted"
                                                        SearchTermination.TIME_BUDGET_EXHAUSTED -> "Time budget of ${request.searchBudget.maxDurationMillis}ms exhausted"
                                                        else -> "Budget exhausted"
                                                    }
                                                )
                                            )
                                        )
                                        levelExhausted = true
                                        break
                                    }

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
                                    nextBeamCandidates.sortWith(stateComparator)
                                    if (nextBeamCandidates.size > request.searchBudget.beamWidth) {
                                        nextBeamCandidates.removeAt(nextBeamCandidates.size - 1)
                                    }
                                }
                            }
                        }
                    }
                    if (levelExhausted) break
                }
                if (levelExhausted) break
            }

            if (levelExhausted) break

            val completeInLevel = nextBeamCandidates.filter { isStateComplete(it) }
            for (state in completeInLevel) {
                evaluateCompleteAndSave(state)
            }

            if (nextBeamCandidates.isEmpty()) {
                levelExhausted = true
            } else {
                currentBeam = nextBeamCandidates
            }
        }

        val termination = interruption ?: SearchTermination.BOUNDED_SEARCH_COMPLETED

        val sortedSolutions = solutions.distinctBy { it.id }.sortedWith(
            compareByDescending<FeasibleSolution<A>> { it.totalScore }
                .thenBy { it.id.value }
        )

        return SolveResult(
            solutions = sortedSolutions,
            termination = termination,
            expandedNodes = guard.expandedNodes,
            trace = trace
        )
    }
}
