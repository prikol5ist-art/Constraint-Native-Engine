package io.github.constraintnative.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class SearchContractsTest {

    @Test
    fun `ConstraintVerdict Violated should require code matching regex`() {
        assertDoesNotThrow { ConstraintVerdict.Violated("valid.code-123_abc", "message") }

        assertThatThrownBy { ConstraintVerdict.Violated("", "message") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ConstraintVerdict.Violated("UPPERCASE", "message") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ConstraintVerdict.Violated("invalid/code", "message") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ConstraintVerdict.Violated("a".repeat(81), "message") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `ScoreComponent should require finite value`() {
        assertDoesNotThrow { ScoreComponent("name", 1.5) }

        assertThatThrownBy { ScoreComponent("name", Double.NaN) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ScoreComponent("name", Double.POSITIVE_INFINITY) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `SearchBudget should require positive values`() {
        assertDoesNotThrow { SearchBudget(100L, 1000L, 10) }

        assertThatThrownBy { SearchBudget(0L, 1000L, 10) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SearchBudget(100L, 0L, 10) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SearchBudget(100L, 1000L, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `SolveRequest should require desiredSolutions in range 1 to 100`() {
        val budget = SearchBudget(100L, 1000L, 10)
        val cand = Candidate(CandidateId("c"), "attr", setOf(SlotId("s")))
        val req = SlotRequirement(SlotId("s"), 1, 1)

        assertDoesNotThrow {
            SolveRequest(
                candidates = listOf(cand),
                slots = listOf(req),
                context = "ctx",
                hardConstraints = emptyList(),
                softScorers = emptyList(),
                desiredSolutions = 5,
                searchMode = SearchMode.EXACT,
                searchBudget = budget
            )
        }

        assertThatThrownBy {
            SolveRequest(
                candidates = listOf(cand),
                slots = listOf(req),
                context = "ctx",
                hardConstraints = emptyList(),
                softScorers = emptyList(),
                desiredSolutions = 0,
                searchMode = SearchMode.EXACT,
                searchBudget = budget
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            SolveRequest(
                candidates = listOf(cand),
                slots = listOf(req),
                context = "ctx",
                hardConstraints = emptyList(),
                softScorers = emptyList(),
                desiredSolutions = 101,
                searchMode = SearchMode.EXACT,
                searchBudget = budget
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `SolveRequest should require beamWidth greater than or equal to desiredSolutions`() {
        val cand = Candidate(CandidateId("c"), "attr", setOf(SlotId("s")))
        val req = SlotRequirement(SlotId("s"), 1, 1)

        assertThatThrownBy {
            SolveRequest(
                candidates = listOf(cand),
                slots = listOf(req),
                context = "ctx",
                hardConstraints = emptyList(),
                softScorers = emptyList(),
                desiredSolutions = 15,
                searchMode = SearchMode.EXACT,
                searchBudget = SearchBudget(100L, 1000L, 10)
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `SolveRequest should require unique candidate IDs and unique slot IDs`() {
        val budget = SearchBudget(100L, 1000L, 10)
        val s = SlotId("s")
        val cand1 = Candidate(CandidateId("c"), "attr", setOf(s))
        val req = SlotRequirement(s, 1, 1)

        // Duplicate candidate
        assertThatThrownBy {
            SolveRequest(
                candidates = listOf(cand1, cand1),
                slots = listOf(req),
                context = "ctx",
                hardConstraints = emptyList(),
                softScorers = emptyList(),
                desiredSolutions = 5,
                searchMode = SearchMode.EXACT,
                searchBudget = budget
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        // Duplicate slot requirement
        assertThatThrownBy {
            SolveRequest(
                candidates = listOf(cand1),
                slots = listOf(req, req),
                context = "ctx",
                hardConstraints = emptyList(),
                softScorers = emptyList(),
                desiredSolutions = 5,
                searchMode = SearchMode.EXACT,
                searchBudget = budget
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `TraceEvent serialization and sanitization rules`() {
        val multiline = "first line\nsecond line\rthird line"
        val cleanMsg = TraceEvent.sanitizeMessage(multiline)
        assert(cleanMsg == "first line second line third line")

        val longMsg = "a".repeat(600)
        val cleanLong = TraceEvent.sanitizeMessage(longMsg)
        assert(cleanLong.length == 512)
    }

    @Test
    fun `bounded search termination is a public enum value`() {
        assertThat(SearchTermination.valueOf("BOUNDED_SEARCH_COMPLETED"))
            .isEqualTo(SearchTermination.BOUNDED_SEARCH_COMPLETED)
    }

    @Test
    fun `TraceEvent rejects unsanitized reasons`() {
        assertThatThrownBy {
            TraceEvent.CandidateRejected(
                sequence = 0,
                candidateId = CandidateId("candidate"),
                slotId = SlotId("slot"),
                reason = "first\nsecond"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            TraceEvent.SearchStopped(sequence = 1, reason = "x".repeat(513))
        }.isInstanceOf(IllegalArgumentException::class.java)

        val sanitized = TraceEvent.sanitizeMessage("first\nsecond" + "x".repeat(600))
        val event = TraceEvent.SearchStopped(sequence = 2, reason = sanitized)
        assertThat(event.reason).doesNotContain("\n", "\r")
        assertThat(event.reason.length).isLessThanOrEqualTo(512)
    }

    @Test
    fun `test data class boilerplate coverage`() {
        val budget = SearchBudget(100L, 1000L, 10)
        val budget2 = budget.copy(maxExpandedNodes = 200L)
        assertThat(budget == budget2).isFalse()
        assertThat(budget.toString()).isNotEmpty()
        assertThat(budget.hashCode()).isNotZero()

        val cand = Candidate(CandidateId("c"), "attr", setOf(SlotId("s")))
        val req = SlotRequirement(SlotId("s"), 1, 1)
        val request = SolveRequest(
            candidates = listOf(cand),
            slots = listOf(req),
            context = "ctx",
            hardConstraints = emptyList(),
            softScorers = emptyList(),
            desiredSolutions = 5,
            searchMode = SearchMode.EXACT,
            searchBudget = budget
        )
        val request2 = request.copy(desiredSolutions = 6)
        assertThat(request == request2).isFalse()
        assertThat(request.toString()).isNotEmpty()
        assertThat(request.hashCode()).isNotZero()

        val assignment = Assignment(SlotId("s"), cand)
        val assignment2 = assignment.copy(slot = SlotId("s"))
        assertThat(assignment == assignment2).isTrue()
        assertThat(assignment.toString()).isNotEmpty()
        assertThat(assignment.hashCode()).isNotZero()

        val partial = PartialAssembly(listOf(assignment), true)
        val partial2 = partial.copy(complete = false)
        assertThat(partial == partial2).isFalse()
        assertThat(partial.toString()).isNotEmpty()
        assertThat(partial.hashCode()).isNotZero()

        val sc = ScoreComponent("sc", 10.0)
        val sc2 = sc.copy(value = 5.0)
        assertThat(sc == sc2).isFalse()
        assertThat(sc.toString()).isNotEmpty()
        assertThat(sc.hashCode()).isNotZero()

        val solution = FeasibleSolution(
            id = SolutionId("sol"),
            assignments = listOf(assignment),
            scoreComponents = listOf(sc),
            totalScore = 10.0,
            explanation = "exp"
        )
        val solution2 = solution.copy(explanation = null)
        assertThat(solution == solution2).isFalse()
        assertThat(solution.toString()).isNotEmpty()
        assertThat(solution.hashCode()).isNotZero()

        val solveResult = SolveResult(
            solutions = listOf(solution),
            termination = SearchTermination.EXHAUSTIVE,
            expandedNodes = 100L,
            trace = emptyList()
        )
        val solveResult2 = solveResult.copy(expandedNodes = 50L)
        assertThat(solveResult == solveResult2).isFalse()
        assertThat(solveResult.toString()).isNotEmpty()
        assertThat(solveResult.hashCode()).isNotZero()

        val event1 = TraceEvent.CandidateRejected(1L, CandidateId("c"), SlotId("s"), "r")
        val event1Copy = event1.copy(reason = "r2")
        assertThat(event1 == event1Copy).isFalse()
        assertThat(event1.toString()).isNotEmpty()
        assertThat(event1.hashCode()).isNotZero()

        val event2 = TraceEvent.PartialAssemblyPruned(1L, SlotId("s"), "r")
        val event2Copy = event2.copy(reason = "r2")
        assertThat(event2 == event2Copy).isFalse()
        assertThat(event2.toString()).isNotEmpty()
        assertThat(event2.hashCode()).isNotZero()

        val event3 = TraceEvent.SolutionAccepted(1L, SolutionId("sol"), 10.0)
        val event3Copy = event3.copy(score = 5.0)
        assertThat(event3 == event3Copy).isFalse()
        assertThat(event3.toString()).isNotEmpty()
        assertThat(event3.hashCode()).isNotZero()

        val event4 = TraceEvent.SearchStopped(1L, "r")
        val event4Copy = event4.copy(reason = "r2")
        assertThat(event4 == event4Copy).isFalse()
        assertThat(event4.toString()).isNotEmpty()
        assertThat(event4.hashCode()).isNotZero()
    }
}
