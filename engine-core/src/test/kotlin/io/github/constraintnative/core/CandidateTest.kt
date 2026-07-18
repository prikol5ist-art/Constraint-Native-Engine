package io.github.constraintnative.core

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class CandidateTest {

    @Test
    fun `Candidate should require at least one eligible slot`() {
        val slot = SlotId("slot1")
        assertDoesNotThrow {
            Candidate(
                id = CandidateId("cand1"),
                attributes = "attr",
                eligibleSlots = setOf(slot)
            )
        }

        assertThatThrownBy {
            Candidate(
                id = CandidateId("cand1"),
                attributes = "attr",
                eligibleSlots = emptySet()
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `SlotRequirement should require min non-negative and max greater than or equal to min`() {
        val slot = SlotId("slot1")
        assertDoesNotThrow { SlotRequirement(slot, 0, 1) }
        assertDoesNotThrow { SlotRequirement(slot, 1, 1) }
        assertDoesNotThrow { SlotRequirement(slot, 0, 0) }

        assertThatThrownBy { SlotRequirement(slot, -1, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SlotRequirement(slot, 2, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `Assignment should require candidate to be eligible for the slot`() {
        val eligibleSlot = SlotId("slot1")
        val ineligibleSlot = SlotId("slot2")
        val candidate = Candidate(
            id = CandidateId("cand1"),
            attributes = "attr",
            eligibleSlots = setOf(eligibleSlot)
        )

        assertDoesNotThrow { Assignment(eligibleSlot, candidate) }

        assertThatThrownBy { Assignment(ineligibleSlot, candidate) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `PartialAssembly should require unique candidates`() {
        val slot = SlotId("slot1")
        val candidate1 = Candidate(CandidateId("cand1"), "attr", setOf(slot))
        val candidate2 = Candidate(CandidateId("cand2"), "attr", setOf(slot))

        assertDoesNotThrow {
            PartialAssembly(
                assignments = listOf(
                    Assignment(slot, candidate1),
                    Assignment(slot, candidate2)
                ),
                complete = false
            )
        }

        assertThatThrownBy {
            PartialAssembly(
                assignments = listOf(
                    Assignment(slot, candidate1),
                    Assignment(slot, candidate1)
                ),
                complete = false
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
