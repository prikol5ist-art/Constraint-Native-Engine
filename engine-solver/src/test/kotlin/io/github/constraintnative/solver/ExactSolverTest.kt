package io.github.constraintnative.solver

import io.github.constraintnative.core.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Collections

class ExactSolverTest {

    private val slotTop = SlotId("top")
    private val slotBottom = SlotId("bottom")
    private val slotShoes = SlotId("shoes")

    private val top1 = Candidate(CandidateId("top1"), "Top 1", setOf(slotTop))
    private val top2 = Candidate(CandidateId("top2"), "Top 2", setOf(slotTop))
    private val bottom1 = Candidate(CandidateId("bottom1"), "Bottom 1", setOf(slotBottom))
    private val shoes1 = Candidate(CandidateId("shoes1"), "Shoes 1", setOf(slotShoes))

    private val defaultBudget = SearchBudget(
        maxExpandedNodes = 1000L,
        maxDurationMillis = 5000L,
        beamWidth = 10
    )

    @Test
    fun `should find two expected solutions for simple outfit request`() {
        val solver = DeterministicSolver()
        val request = SolveRequest(
            candidates = listOf(top1, top2, bottom1, shoes1),
            slots = listOf(
                SlotRequirement(slotTop, 1, 1),
                SlotRequirement(slotBottom, 1, 1),
                SlotRequirement(slotShoes, 1, 1)
            ),
            context = Unit,
            hardConstraints = emptyList(),
            softScorers = listOf(
                SoftScorer { _, _ -> ScoreComponent("default", 1.0) }
            ),
            desiredSolutions = 2,
            searchMode = SearchMode.EXACT,
            searchBudget = defaultBudget,
            seed = 42L
        )

        val result = solver.solve(request)

        assertThat(result.solutions).hasSize(2)
        assertThat(result.termination).isEqualTo(SearchTermination.SOLUTION_LIMIT_REACHED)

        val firstSol = result.solutions[0]
        val secondSol = result.solutions[1]

        assertThat(firstSol.assignments.map { it.candidate.id })
            .containsExactlyInAnyOrder(CandidateId("top1"), CandidateId("bottom1"), CandidateId("shoes1"))
        assertThat(secondSol.assignments.map { it.candidate.id })
            .containsExactlyInAnyOrder(CandidateId("top2"), CandidateId("bottom1"), CandidateId("shoes1"))
    }

    @Test
    fun `should respect forbidden pairs constraint`() {
        val solver = DeterministicSolver()
        val forbidden = ForbiddenPairConstraint<String, Unit>(
            first = CandidateId("top1"),
            second = CandidateId("bottom1")
        )

        val request = SolveRequest(
            candidates = listOf(top1, top2, bottom1, shoes1),
            slots = listOf(
                SlotRequirement(slotTop, 1, 1),
                SlotRequirement(slotBottom, 1, 1),
                SlotRequirement(slotShoes, 1, 1)
            ),
            context = Unit,
            hardConstraints = listOf(forbidden),
            softScorers = emptyList(),
            desiredSolutions = 2,
            searchMode = SearchMode.EXACT,
            searchBudget = defaultBudget,
            seed = 42L
        )

        val result = solver.solve(request)

        // Only [top2, bottom1, shoes1] is feasible because top1 + bottom1 is forbidden
        assertThat(result.solutions).hasSize(1)
        assertThat(result.solutions[0].assignments.map { it.candidate.id })
            .containsExactlyInAnyOrder(CandidateId("top2"), CandidateId("bottom1"), CandidateId("shoes1"))
        assertThat(result.termination).isEqualTo(SearchTermination.EXHAUSTIVE)
    }

    @Test
    fun `should return INFEASIBLE when no solutions exist`() {
        val solver = DeterministicSolver()
        val request = SolveRequest(
            candidates = listOf(top1, bottom1), // missing shoes
            slots = listOf(
                SlotRequirement(slotTop, 1, 1),
                SlotRequirement(slotBottom, 1, 1),
                SlotRequirement(slotShoes, 1, 1)
            ),
            context = Unit,
            hardConstraints = emptyList(),
            softScorers = emptyList(),
            desiredSolutions = 2,
            searchMode = SearchMode.EXACT,
            searchBudget = defaultBudget,
            seed = 42L
        )

        val result = solver.solve(request)
        assertThat(result.solutions).isEmpty()
        assertThat(result.termination).isEqualTo(SearchTermination.INFEASIBLE)
    }

    @Test
    fun `shuffling input candidates 100 times should yield identical results`() {
        val candidatesList = mutableListOf(top1, top2, bottom1, shoes1)
        val solver = DeterministicSolver()

        val expectedIds = listOf(
            SolutionCanonicalizer.canonicalize(listOf(Assignment(slotTop, top1), Assignment(slotBottom, bottom1), Assignment(slotShoes, shoes1))),
            SolutionCanonicalizer.canonicalize(listOf(Assignment(slotTop, top2), Assignment(slotBottom, bottom1), Assignment(slotShoes, shoes1)))
        )

        for (i in 1..100) {
            Collections.shuffle(candidatesList)
            val request = SolveRequest(
                candidates = candidatesList.toList(),
                slots = listOf(
                    SlotRequirement(slotTop, 1, 1),
                    SlotRequirement(slotBottom, 1, 1),
                    SlotRequirement(slotShoes, 1, 1)
                ),
                context = Unit,
                hardConstraints = emptyList(),
                softScorers = emptyList(),
                desiredSolutions = 2,
                searchMode = SearchMode.EXACT,
                searchBudget = defaultBudget,
                seed = 42L
            )

            val result = solver.solve(request)
            assertThat(result.solutions).hasSize(2)
            val resultIds = result.solutions.map { it.id }
            assertThat(resultIds).containsExactlyInAnyOrderElementsOf(expectedIds)
        }
    }
}
