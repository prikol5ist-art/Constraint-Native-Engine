package io.github.constraintnative.solver

import io.github.constraintnative.core.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SolutionCanonicalizerTest {

    private val slotA = SlotId("slotA")
    private val slotB = SlotId("slotB")

    private val cand1 = Candidate(CandidateId("cand1"), "attr", setOf(slotA))
    private val cand2 = Candidate(CandidateId("cand2"), "attr", setOf(slotB))

    @Test
    fun `should generate same ID regardless of assignment order`() {
        val assignments1 = listOf(
            Assignment(slotA, cand1),
            Assignment(slotB, cand2)
        )
        val assignments2 = listOf(
            Assignment(slotB, cand2),
            Assignment(slotA, cand1)
        )

        val id1 = SolutionCanonicalizer.canonicalize(assignments1)
        val id2 = SolutionCanonicalizer.canonicalize(assignments2)

        assertThat(id1).isEqualTo(id2)
    }

    @Test
    fun `should generate different IDs for different assignments`() {
        val assignments1 = listOf(Assignment(slotA, cand1))
        val assignments2 = listOf(Assignment(slotB, cand2))

        val id1 = SolutionCanonicalizer.canonicalize(assignments1)
        val id2 = SolutionCanonicalizer.canonicalize(assignments2)

        assertThat(id1).isNotEqualTo(id2)
    }

    @Test
    fun `should match pinned digest test vector`() {
        val assignments = listOf(
            Assignment(slotA, cand1),
            Assignment(slotB, cand2)
        )
        val solutionId = SolutionCanonicalizer.canonicalize(assignments)
        assertThat(solutionId.value).isEqualTo("19a09098df7d4aab67ffd85b65f08cf2")
    }


    @Test
    fun `distinguishes legal IDs that collide in delimiter encoding`() {
        val firstSlot = SlotId("a")
        val firstCandidate = Candidate(CandidateId("b=c"), "first", setOf(firstSlot))
        val secondSlot = SlotId("a=b")
        val secondCandidate = Candidate(CandidateId("c"), "second", setOf(secondSlot))

        val first = SolutionCanonicalizer.canonicalize(listOf(Assignment(firstSlot, firstCandidate)))
        val second = SolutionCanonicalizer.canonicalize(listOf(Assignment(secondSlot, secondCandidate)))

        assertThat(first).isNotEqualTo(second)
    }
}
