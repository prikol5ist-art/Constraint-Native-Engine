package io.github.constraintnative.core

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
                searchBudget = budget,
                seed = 42L
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
                searchBudget = budget,
                seed = 42L
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
                searchBudget = budget,
                seed = 42L
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
                searchBudget = SearchBudget(100L, 1000L, 10),
                seed = 42L
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
                searchBudget = budget,
                seed = 42L
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
                searchBudget = budget,
                seed = 42L
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
}
