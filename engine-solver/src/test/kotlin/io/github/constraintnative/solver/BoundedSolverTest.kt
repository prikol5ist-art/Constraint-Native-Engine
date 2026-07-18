package io.github.constraintnative.solver

import io.github.constraintnative.core.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

class BoundedSolverTest {

    private val slotTop = SlotId("top")
    private val slotBottom = SlotId("bottom")

    private val top1 = Candidate(CandidateId("top1"), "Top 1", setOf(slotTop))
    private val top2 = Candidate(CandidateId("top2"), "Top 2", setOf(slotTop))
    private val top3 = Candidate(CandidateId("top3"), "Top 3", setOf(slotTop))
    private val bottom1 = Candidate(CandidateId("bottom1"), "Bottom 1", setOf(slotBottom))

    @Test
    fun `should find solutions in bounded beam mode`() {
        val solver = DeterministicSolver()
        val request = SolveRequest(
            candidates = listOf(top1, top2, bottom1),
            slots = listOf(
                SlotRequirement(slotTop, 1, 1),
                SlotRequirement(slotBottom, 1, 1)
            ),
            context = Unit,
            hardConstraints = emptyList(),
            softScorers = listOf(
                SoftScorer { assembly, _ ->
                    val isTop1 = assembly.assignments.any { it.candidate.id.value == "top1" }
                    val score = if (isTop1) 10.0 else 2.0
                    ScoreComponent("style", score)
                }
            ),
            desiredSolutions = 2,
            searchMode = SearchMode.BOUNDED_BEAM,
            searchBudget = SearchBudget(1000L, 5000L, 5),
            seed = 42L
        )

        val result = solver.solve(request)

        assertThat(result.solutions).hasSize(2)
        assertThat(result.solutions[0].totalScore).isEqualTo(10.0)
        assertThat(result.solutions[1].totalScore).isEqualTo(2.0)
    }

    @Test
    fun `should respect node budget and return NODE_BUDGET_EXHAUSTED`() {
        val solver = DeterministicSolver()
        val request = SolveRequest(
            candidates = listOf(top1, top2, top3, bottom1),
            slots = listOf(
                SlotRequirement(slotTop, 1, 1),
                SlotRequirement(slotBottom, 1, 1)
            ),
            context = Unit,
            hardConstraints = emptyList(),
            softScorers = emptyList(),
            desiredSolutions = 3,
            searchMode = SearchMode.BOUNDED_BEAM,
            searchBudget = SearchBudget(2L, 5000L, 5), // very small node budget
            seed = 42L
        )

        val result = solver.solve(request)

        assertThat(result.termination).isEqualTo(SearchTermination.NODE_BUDGET_EXHAUSTED)
        assertThat(result.solutions).hasSize(0) // stopped before finding full solutions
    }

    @Test
    fun `should respect time budget and return TIME_BUDGET_EXHAUSTED`() {
        // Fake clock that increments on every read
        val time = AtomicLong(0L)
        val fakeClock = DeterministicSolver.MonotonicClock {
            time.getAndAdd(1000L) // 1000ms increment per read
        }
        val solver = DeterministicSolver(fakeClock)

        val request = SolveRequest(
            candidates = listOf(top1, top2, bottom1),
            slots = listOf(
                SlotRequirement(slotTop, 1, 1),
                SlotRequirement(slotBottom, 1, 1)
            ),
            context = Unit,
            hardConstraints = emptyList(),
            softScorers = emptyList(),
            desiredSolutions = 2,
            searchMode = SearchMode.BOUNDED_BEAM,
            searchBudget = SearchBudget(100L, 500L, 5), // max duration 500ms
            seed = 42L
        )

        val result = solver.solve(request)

        assertThat(result.termination).isEqualTo(SearchTermination.TIME_BUDGET_EXHAUSTED)
    }

    @Test
    fun `should prune beam width to max value`() {
        val solver = DeterministicSolver()
        val request = SolveRequest(
            candidates = listOf(top1, top2, top3, bottom1),
            slots = listOf(
                SlotRequirement(slotTop, 1, 1),
                SlotRequirement(slotBottom, 1, 1)
            ),
            context = Unit,
            hardConstraints = emptyList(),
            softScorers = listOf(
                SoftScorer { assembly, _ ->
                    val topId = assembly.assignments.find { it.slot == slotTop }?.candidate?.id?.value
                    val score = when (topId) {
                        "top1" -> 10.0
                        "top2" -> 5.0
                        "top3" -> 1.0
                        else -> 0.0
                    }
                    ScoreComponent("style", score)
                }
            ),
            desiredSolutions = 2,
            searchMode = SearchMode.BOUNDED_BEAM,
            searchBudget = SearchBudget(1000L, 5000L, 2), // beam width of 2
            seed = 42L
        )

        val result = solver.solve(request)

        // With beam width 2, only top1 and top2 options will be retained in beam level 1.
        // Therefore, top3 + bottom1 will be pruned and cannot form a solution.
        assertThat(result.solutions).hasSize(2)
        val selectedTopIds = result.solutions.flatMap { sol ->
            sol.assignments.filter { it.slot == slotTop }.map { it.candidate.id.value }
        }
        assertThat(selectedTopIds).containsExactlyInAnyOrder("top1", "top2")
    }
}
