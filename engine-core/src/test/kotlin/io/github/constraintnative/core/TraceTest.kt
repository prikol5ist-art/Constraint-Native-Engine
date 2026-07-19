package io.github.constraintnative.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class TraceTest {

    @Test
    fun `TraceEvent companion should sanitize multiline messages`() {
        val raw = "line1\nline2\rline3"
        val sanitized = TraceEvent.sanitizeMessage(raw)
        assertThat(sanitized).isEqualTo("line1 line2 line3")
    }

    @Test
    fun `TraceEvent companion should truncate long messages`() {
        val longMsg = "x".repeat(600)
        val sanitized = TraceEvent.sanitizeMessage(longMsg)
        assertThat(sanitized).hasSize(512)
    }

    @Test
    fun `CandidateRejected validation and message`() {
        val event = TraceEvent.CandidateRejected(
            sequence = 1L,
            candidateId = CandidateId("c1"),
            slotId = SlotId("s1"),
            reason = "Some valid reason"
        )
        assertThat(event.message).contains("rejected")

        assertThatThrownBy {
            TraceEvent.CandidateRejected(1L, CandidateId("c1"), SlotId("s1"), "invalid\nreason")
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            TraceEvent.CandidateRejected(1L, CandidateId("c1"), SlotId("s1"), "a".repeat(513))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `PartialAssemblyPruned validation and message`() {
        val event = TraceEvent.PartialAssemblyPruned(
            sequence = 1L,
            slotId = SlotId("s1"),
            reason = "Some valid reason"
        )
        assertThat(event.message).contains("pruned")

        assertThatThrownBy {
            TraceEvent.PartialAssemblyPruned(1L, SlotId("s1"), "invalid\nreason")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `SolutionAccepted message`() {
        val event = TraceEvent.SolutionAccepted(
            sequence = 1L,
            solutionId = SolutionId("sol1"),
            score = 10.0
        )
        assertThat(event.message).contains("accepted")
    }

    @Test
    fun `SearchStopped validation and message`() {
        val event = TraceEvent.SearchStopped(
            sequence = 1L,
            reason = "Some valid reason"
        )
        assertThat(event.message).contains("stopped")

        assertThatThrownBy {
            TraceEvent.SearchStopped(1L, "invalid\nreason")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
