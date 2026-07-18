package io.github.constraintnative.generative

import io.github.constraintnative.core.*
import io.github.constraintnative.solver.DeterministicSolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class ConstraintNativeEngineTest {

    private val slotTop = SlotId("top")
    private val slotBottom = SlotId("bottom")

    private val top1 = Candidate(CandidateId("top1"), "Top 1", setOf(slotTop))
    private val top2 = Candidate(CandidateId("top2"), "Top 2", setOf(slotTop))
    private val bottom1 = Candidate(CandidateId("bottom1"), "Bottom 1", setOf(slotBottom))

    private val defaultBudget = SearchBudget(
        maxExpandedNodes = 1000L,
        maxDurationMillis = 5000L,
        beamWidth = 10
    )

    private val solveRequest = SolveRequest(
        candidates = listOf(top1, top2, bottom1),
        slots = listOf(
            SlotRequirement(slotTop, 1, 1),
            SlotRequirement(slotBottom, 1, 1)
        ),
        context = "nice weather",
        hardConstraints = emptyList(),
        softScorers = emptyList(),
        desiredSolutions = 2,
        searchMode = SearchMode.EXACT,
        searchBudget = defaultBudget,
        seed = 42L
    )

    private val testRenderer = object : ContextRenderer<String, String> {
        override fun renderContext(context: String): String = context
        override fun renderSolution(solution: FeasibleSolution<String>): String {
            return solution.assignments.joinToString { "${it.slot.value}=${it.candidate.id.value}" }
        }
    }

    @Test
    fun `should return local order when ranker is not configured`() = runTest {
        val engine = ConstraintNativeEngine(DeterministicSolver())
        val result = engine.generate(solveRequest, ranker = null, renderer = null)

        assertThat(result.rankerStatus).isEqualTo(RankerStatus.NOT_CONFIGURED)
        assertThat(result.solutions).hasSize(2)
    }

    @Test
    fun `should return ranked order and attach explanations when ranker is successful`() = runTest {
        val engine = ConstraintNativeEngine(DeterministicSolver())

        val mockRanker = GenerativeRanker { request ->
            // Let's rank sol2 (top2 + bottom1) first
            val sol2Id = request.solutions.find { it.summary.contains("top2") }!!.id
            val sol1Id = request.solutions.find { it.summary.contains("top1") }!!.id
            RankingResponse(
                listOf(
                    RankedSolution(sol2Id, "Explanation 2"),
                    RankedSolution(sol1Id, "Explanation 1")
                )
            )
        }

        val result = engine.generate(solveRequest, ranker = mockRanker, renderer = testRenderer)

        assertThat(result.rankerStatus).isEqualTo(RankerStatus.APPLIED)
        assertThat(result.solutions).hasSize(2)
        // First solution should be top2
        assertThat(result.solutions[0].assignments.find { it.slot == slotTop }?.candidate?.id?.value).isEqualTo("top2")
        assertThat(result.solutions[0].explanation).isEqualTo("Explanation 2")

        // Second solution should be top1
        assertThat(result.solutions[1].assignments.find { it.slot == slotTop }?.candidate?.id?.value).isEqualTo("top1")
        assertThat(result.solutions[1].explanation).isEqualTo("Explanation 1")
    }

    @Test
    fun `should fall back to local order when ranker returns unknown ID`() = runTest {
        val engine = ConstraintNativeEngine(DeterministicSolver())
        val mockRanker = GenerativeRanker {
            RankingResponse(listOf(RankedSolution(SolutionId("unknown_id"))))
        }

        val result = engine.generate(solveRequest, ranker = mockRanker, renderer = testRenderer)

        assertThat(result.rankerStatus).isEqualTo(RankerStatus.FELL_BACK)
        assertThat(result.rankerError).contains("unknown_solution_id")
        assertThat(result.solutions).hasSize(2) // Fallback list is correct size
    }

    @Test
    fun `should fall back to local order when ranker throws exception`() = runTest {
        val engine = ConstraintNativeEngine(DeterministicSolver())
        val mockRanker = GenerativeRanker {
            throw RuntimeException("Ranker failed")
        }

        val result = engine.generate(solveRequest, ranker = mockRanker, renderer = testRenderer)

        assertThat(result.rankerStatus).isEqualTo(RankerStatus.FELL_BACK)
        assertThat(result.rankerError).isEqualTo("Ranker failed")
        assertThat(result.solutions).hasSize(2)
    }

    @Test
    fun `should fall back to local order when ranker times out`() = runTest {
        val engine = ConstraintNativeEngine(DeterministicSolver())
        val mockRanker = GenerativeRanker {
            delay(1000L)
            RankingResponse(emptyList())
        }

        val result = engine.generate(
            solveRequest,
            ranker = mockRanker,
            renderer = testRenderer,
            rankerTimeoutMillis = 50L
        )

        assertThat(result.rankerStatus).isEqualTo(RankerStatus.FELL_BACK)
        assertThat(result.solutions).hasSize(2)
    }

    @Test
    fun `should propagate CancellationException when cancelled`() = runTest {
        val engine = ConstraintNativeEngine(DeterministicSolver())
        val mockRanker = GenerativeRanker {
            throw CancellationException("Job cancelled")
        }

        try {
            engine.generate(solveRequest, ranker = mockRanker, renderer = testRenderer)
            assert(false) { "Expected CancellationException to be thrown" }
        } catch (e: CancellationException) {
            assertThat(e.message).isEqualTo("Job cancelled")
        }
    }

    @Test
    fun `should not call ranker when solve has no feasible solutions`() = runTest {
        val infeasibleRequest = solveRequest.copy(candidates = emptyList())
        val engine = ConstraintNativeEngine(DeterministicSolver())
        var rankerCalled = false
        val mockRanker = GenerativeRanker {
            rankerCalled = true
            RankingResponse(emptyList())
        }

        val result = engine.generate(infeasibleRequest, ranker = mockRanker, renderer = testRenderer)

        assertThat(result.solutions).isEmpty()
        assertThat(rankerCalled).isFalse()
        assertThat(result.rankerStatus).isEqualTo(RankerStatus.NOT_CONFIGURED)
    }
}
