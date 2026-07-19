package io.github.constraintnative.solver

import io.github.constraintnative.core.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RandomizedSolverVerificationTest {

    @Test
    fun `randomized verification of exact vs beam search`() {
        val random = Random(42L)
        val solver = DeterministicSolver()

        val numRuns = 2000
        for (run in 0 until numRuns) {
            val numSlots = 2
            val slots = (0 until numSlots).map { SlotId("slot_$it") }
            val slotReqs = slots.map { SlotRequirement(it, 1, 1) }

            val numCandidates = random.nextInt(3, 6) // 3 to 5 candidates
            val candidates = (0 until numCandidates).map { i ->
                val numEligible = random.nextInt(1, numSlots + 1)
                val eligible = slots.shuffled(random).take(numEligible).toSet()
                Candidate(
                    id = CandidateId("cand_$i"),
                    attributes = Unit,
                    eligibleSlots = eligible
                )
            }

            // Occasional forbidden pair constraints
            val hardConstraints = mutableListOf<HardConstraint<Unit, Unit>>()
            if (random.nextDouble() < 0.5 && numCandidates >= 2) {
                val first = CandidateId("cand_${random.nextInt(numCandidates)}")
                var second = CandidateId("cand_${random.nextInt(numCandidates)}")
                while (second == first) {
                    second = CandidateId("cand_${random.nextInt(numCandidates)}")
                }
                hardConstraints.add(ForbiddenPairConstraint(first, second))
            }

            // Custom soft scorer that assigns random constant scores to candidates
            val candidateScores = candidates.associate { it.id to random.nextDouble(-10.0, 10.0) }
            val softScorers = listOf<SoftScorer<Unit, Unit>>(
                SoftScorer { assembly, _ ->
                    val sum = assembly.assignments.sumOf { candidateScores[it.candidate.id] ?: 0.0 }
                    ScoreComponent("random_score", sum)
                }
            )

            val budget = SearchBudget(
                maxExpandedNodes = 1000L,
                maxDurationMillis = 2000L,
                beamWidth = 100
            )

            // We set desiredSolutions to 100 to ensure Exact finds all possible solutions
            val exactRequest = SolveRequest(
                candidates = candidates,
                slots = slotReqs,
                context = Unit,
                hardConstraints = hardConstraints,
                softScorers = softScorers,
                desiredSolutions = 100,
                searchMode = SearchMode.EXACT,
                searchBudget = budget
            )

            val beamRequest = exactRequest.copy(
                searchMode = SearchMode.BOUNDED_BEAM,
                desiredSolutions = 5,
                searchBudget = budget.copy(beamWidth = 5)
            )

            val exactResult = solver.solve(exactRequest)
            val beamResult = solver.solve(beamRequest)

            // Independently enumerate all valid solutions:
            val allPossibleSolutions = mutableListOf<List<Assignment<Unit>>>()
            for (c0 in candidates) {
                if (slots[0] in c0.eligibleSlots) {
                    for (c1 in candidates) {
                        if (c1.id != c0.id && slots[1] in c1.eligibleSlots) {
                            val assignments = listOf(Assignment(slots[0], c0), Assignment(slots[1], c1))
                            val partialAssembly = PartialAssembly(assignments, complete = true)
                            val satisfiesHard = hardConstraints.all { constraint ->
                                constraint.evaluate(partialAssembly, Unit) !is ConstraintVerdict.Violated
                            }
                            if (satisfiesHard) {
                                allPossibleSolutions.add(assignments)
                            }
                        }
                    }
                }
            }

            val expectedSolutions = allPossibleSolutions.map { assignments ->
                val partialAssembly = PartialAssembly(assignments, complete = true)
                val scoreComponents = softScorers.map { it.score(partialAssembly, Unit) }
                val totalScore = scoreComponents.sumOf { it.value }
                val id = SolutionCanonicalizer.canonicalize(assignments)
                FeasibleSolution(id, assignments, scoreComponents, totalScore)
            }.distinctBy { it.id }.sortedWith(
                compareByDescending<FeasibleSolution<Unit>> { it.totalScore }
                    .thenBy { it.id.value }
            )

            // Assert exact search finds exactly the expected solutions in the correct order
            assertThat(exactResult.solutions).isEqualTo(expectedSolutions)

            if (beamResult.solutions.isNotEmpty()) {
                // Exact search must not be empty if beam search found solutions
                assertThat(exactResult.solutions).isNotEmpty

                // Exact best score must be >= beam best score
                val exactBestScore = exactResult.solutions.first().totalScore
                val beamBestScore = beamResult.solutions.first().totalScore
                assertThat(exactBestScore).isGreaterThanOrEqualTo(beamBestScore - 1e-9)
            }
        }
    }
}
