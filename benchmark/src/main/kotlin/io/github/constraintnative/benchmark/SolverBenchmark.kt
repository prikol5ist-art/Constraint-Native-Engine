package io.github.constraintnative.benchmark

import kotlinx.benchmark.*
import io.github.constraintnative.core.*
import io.github.constraintnative.solver.DeterministicSolver
import io.github.constraintnative.fashion.*

@State(Scope.Benchmark)
public open class SolverBenchmark {

    private val solver = DeterministicSolver()

    @Param("2", "3", "4")
    public var slotSize: Int = 3

    private lateinit var exactRequest: SolveRequest<FashionCandidate, FashionContext>
    private lateinit var beamRequest: SolveRequest<FashionCandidate, FashionContext>

    @Setup
    public fun setup() {
        val slots = (1..slotSize).map { SlotId("slot_$it") }
        val slotReqs = slots.map { SlotRequirement(it, 1, 1) }

        val candidates = mutableListOf<Candidate<FashionCandidate>>()
        for (i in 0 until (slotSize * 3)) {
            val el = slots.toSet()
            candidates.add(
                Candidate(
                    id = CandidateId("cand_$i"),
                    attributes = FashionCandidate("cand_$i", "Candidate $i", "Shirt", "red", 1, 0.5),
                    eligibleSlots = el
                )
            )
        }

        val budget = SearchBudget(
            maxExpandedNodes = 10000L,
            maxDurationMillis = 5000L,
            beamWidth = 10
        )

        exactRequest = SolveRequest(
            candidates = candidates,
            slots = slotReqs,
            context = FashionContext(20.0, Precipitation.NONE, Occasion.CASUAL),
            hardConstraints = emptyList(),
            softScorers = listOf(TemperatureScorer(), OccasionScorer(), LayerScorer(), ColorHarmonyScorer()),
            desiredSolutions = 5,
            searchMode = SearchMode.EXACT,
            searchBudget = budget
        )

        beamRequest = exactRequest.copy(
            searchMode = SearchMode.BOUNDED_BEAM
        )
    }

    @Benchmark
    public fun benchmarkExact(): SolveResult<FashionCandidate> {
        return solver.solve(exactRequest)
    }

    @Benchmark
    public fun benchmarkBeam(): SolveResult<FashionCandidate> {
        return solver.solve(beamRequest)
    }
}
