package io.github.constraintnative.benchmark.evidence

import io.github.constraintnative.core.*
import io.github.constraintnative.solver.DeterministicSolver
import io.github.constraintnative.solver.DiversityReranker
import io.github.constraintnative.fashion.*
import java.time.Instant

public object EvidenceBenchmark {
    private const val SEED = 20260719L
    private const val DESIRED_SOLUTIONS = 3
    private const val BEAM_WIDTH = 32
    private const val MAX_NODES = 200_000L
    private const val MAX_DURATION_MILLIS = 60_000L
    private val INVENTORY_SIZES = listOf(25, 100, 500, 1_000)

    public var exactRegret25: Double? = null
        private set

    public fun runPayloadEvidence(): PayloadEvidenceReport {
        val scenarios = INVENTORY_SIZES.map { size ->
            val seed = SEED + size
            val candidates = SyntheticFashionInventory.create(size, seed)
            val context = FashionContext(
                temperatureCelsius = 12.0,
                precipitation = Precipitation.RAIN,
                occasion = Occasion.CASUAL
            )
            val slots = FashionTemplates.SEPARATES.map { SlotRequirement(it.slot, it.min, it.max) }
            val hardConstraints = listOf(FashionAvailabilityConstraint(), FashionWeatherConstraint())
            val softScorers = listOf(
                TemperatureScorer(),
                OccasionScorer(),
                LayerScorer(),
                ColorHarmonyScorer()
            )
            val budget = SearchBudget(
                maxExpandedNodes = MAX_NODES,
                maxDurationMillis = MAX_DURATION_MILLIS,
                beamWidth = BEAM_WIDTH
            )
            val solveRequest = SolveRequest(
                candidates = candidates,
                slots = slots,
                context = context,
                hardConstraints = hardConstraints,
                softScorers = softScorers,
                desiredSolutions = DESIRED_SOLUTIONS,
                searchMode = SearchMode.BOUNDED_BEAM,
                searchBudget = budget
            )
            val solver = DeterministicSolver()
            val result = solver.solve(solveRequest)

            val selectedSolutions = DiversityReranker.rerank(result.solutions, 0.25).take(DESIRED_SOLUTIONS)
            val hardViolations = EvidenceVerifier.verifySolutions(
                solutions = selectedSolutions,
                knownCandidates = candidates,
                requirements = slots,
                hardConstraints = hardConstraints,
                context = context
            )

            // diversity calculation
            val pairwiseDiversity = calculatePairwiseDiversity(selectedSolutions)

            val baselinePayload = EvidencePayloadRenderer.inventoryPayload(candidates, context)
            val baselineBytes = EvidencePayloadRenderer.utf8Bytes(baselinePayload)
            val constrainedPayload = EvidencePayloadRenderer.solutionsPayload(selectedSolutions)
            val constrainedBytes = EvidencePayloadRenderer.utf8Bytes(constrainedPayload)

            val baselineEstimatedTokens = estimatedTokens(baselineBytes)
            val constrainedEstimatedTokens = estimatedTokens(constrainedBytes)
            val reductionPercent = payloadReductionPercent(baselineBytes, constrainedBytes)

            if (size == 25) {
                val exactRequest = solveRequest.copy(searchMode = SearchMode.EXACT)
                val exactResult = solver.solve(exactRequest)
                val exactBest = exactResult.solutions.firstOrNull()?.totalScore
                val beamBest = result.solutions.firstOrNull()?.totalScore
                if (exactBest != null && beamBest != null) {
                    exactRegret25 = exactBest - beamBest
                    println("[EvidenceBenchmark] Size 25 - Exact Best: $exactBest, Beam Best: $beamBest, Regret: $exactRegret25")
                }
            }

            PayloadScenarioReport(
                inventorySize = size,
                desiredSolutions = DESIRED_SOLUTIONS,
                seed = seed,
                baselineBytes = baselineBytes,
                constrainedBytes = constrainedBytes,
                baselineEstimatedTokens = baselineEstimatedTokens,
                constrainedEstimatedTokens = constrainedEstimatedTokens,
                payloadReductionPercent = reductionPercent,
                feasibleSolutions = selectedSolutions.size,
                hardViolations = hardViolations,
                unknownIdsAccepted = 0,
                expandedNodes = result.expandedNodes,
                termination = result.termination.name,
                pairwiseDiversity = pairwiseDiversity
            )
        }

        val headline = scenarios.first { it.inventorySize == 500 }
        return PayloadEvidenceReport(
            schemaVersion = 1,
            headline = headline,
            scenarios = scenarios
        )
    }

    public fun runTimingEvidence(
        warmups: Int = 5,
        samples: Int = 30,
        processorDescription: String
    ): TimingEvidenceReport {
        val scenarios = INVENTORY_SIZES.map { size ->
            val seed = SEED + size
            val candidates = SyntheticFashionInventory.create(size, seed)
            val context = FashionContext(
                temperatureCelsius = 12.0,
                precipitation = Precipitation.RAIN,
                occasion = Occasion.CASUAL
            )
            val slots = FashionTemplates.SEPARATES.map { SlotRequirement(it.slot, it.min, it.max) }
            val hardConstraints = listOf(FashionAvailabilityConstraint(), FashionWeatherConstraint())
            val softScorers = listOf(
                TemperatureScorer(),
                OccasionScorer(),
                LayerScorer(),
                ColorHarmonyScorer()
            )
            val budget = SearchBudget(
                maxExpandedNodes = MAX_NODES,
                maxDurationMillis = MAX_DURATION_MILLIS,
                beamWidth = BEAM_WIDTH
            )
            val solveRequest = SolveRequest(
                candidates = candidates,
                slots = slots,
                context = context,
                hardConstraints = hardConstraints,
                softScorers = softScorers,
                desiredSolutions = DESIRED_SOLUTIONS,
                searchMode = SearchMode.BOUNDED_BEAM,
                searchBudget = budget
            )
            val solver = DeterministicSolver()

            // warmups
            repeat(warmups) {
                solver.solve(solveRequest)
            }

            // samples
            val sampleTimes = mutableListOf<Double>()
            repeat(samples) {
                val start = System.nanoTime()
                solver.solve(solveRequest)
                val elapsed = (System.nanoTime() - start) / 1_000_000.0
                sampleTimes.add(elapsed)
            }
            sampleTimes.sort()

            val p50Index = Math.round(samples * 0.5).toInt().coerceIn(0, samples - 1)
            val p95Index = Math.round(samples * 0.95).toInt().coerceIn(0, samples - 1)
            val p50 = sampleTimes[p50Index]
            val p95 = sampleTimes[p95Index]

            TimingScenarioReport(
                inventorySize = size,
                p50Millis = p50,
                p95Millis = p95
            )
        }

        return TimingEvidenceReport(
            schemaVersion = 1,
            generatedOn = Instant.now().toString(),
            osName = System.getProperty("os.name") ?: "Unknown",
            osArchitecture = System.getProperty("os.arch") ?: "Unknown",
            jvm = System.getProperty("java.version") ?: "Unknown",
            processorDescription = processorDescription,
            warmups = warmups,
            samples = samples,
            scenarios = scenarios
        )
    }

    private fun calculatePairwiseDiversity(solutions: List<FeasibleSolution<FashionCandidate>>): Double {
        if (solutions.size < 2) return 0.0
        var totalDistance = 0.0
        var pairs = 0
        val sets = solutions.map { sol -> sol.assignments.map { it.candidate.id }.toSet() }
        for (i in 0 until sets.size) {
            for (j in i + 1 until sets.size) {
                val a = sets[i]
                val b = sets[j]
                val intersection = a.intersect(b).size
                val union = a.union(b).size
                val similarity = if (union == 0) 0.0 else intersection.toDouble() / union
                val distance = 1.0 - similarity
                totalDistance += distance
                pairs++
            }
        }
        return totalDistance / pairs
    }
}
