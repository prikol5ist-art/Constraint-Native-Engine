package io.github.constraintnative.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BuiltInConstraintsTest {

    private val slotA = SlotId("slotA")
    private val slotB = SlotId("slotB")
    private val slotC = SlotId("slotC")

    private val cand1 = Candidate(CandidateId("c1"), "attr1", setOf(slotA))
    private val cand2 = Candidate(CandidateId("c2"), "attr2", setOf(slotB))
    private val cand3 = Candidate(CandidateId("c3"), "attr3", setOf(slotC))

    @Test
    fun `AttributePredicateConstraint evaluation`() {
        val constraint = AttributePredicateConstraint<String, String>(
            slot = slotA,
            code = "attr_check"
        ) { attr, context -> attr == context }

        // Undetermined when slot not assigned
        val emptyAssembly = PartialAssembly<String>(emptyList(), complete = false)
        assertThat(constraint.evaluate(emptyAssembly, "attr1"))
            .isEqualTo(ConstraintVerdict.Undetermined)

        // Satisfied when predicate passes
        val satisfiedAssembly = PartialAssembly(listOf(Assignment(slotA, cand1)), complete = false)
        assertThat(constraint.evaluate(satisfiedAssembly, "attr1"))
            .isEqualTo(ConstraintVerdict.Satisfied)

        // Violated when predicate fails
        assertThat(constraint.evaluate(satisfiedAssembly, "different_ctx"))
            .isInstanceOf(ConstraintVerdict.Violated::class.java)
    }

    @Test
    fun `ForbiddenPairConstraint evaluation`() {
        val constraint = ForbiddenPairConstraint<String, String>(
            first = CandidateId("c1"),
            second = CandidateId("c2"),
            code = "no_c1_c2"
        )

        val assemblyNone = PartialAssembly<String>(emptyList(), complete = false)
        assertThat(constraint.evaluate(assemblyNone, ""))
            .isEqualTo(ConstraintVerdict.Undetermined)

        val assemblyOne = PartialAssembly(listOf(Assignment(slotA, cand1)), complete = false)
        assertThat(constraint.evaluate(assemblyOne, ""))
            .isEqualTo(ConstraintVerdict.Undetermined)

        val assemblyBoth = PartialAssembly(
            listOf(Assignment(slotA, cand1), Assignment(slotB, cand2)),
            complete = false
        )
        val verdict = constraint.evaluate(assemblyBoth, "")
        assertThat(verdict).isInstanceOf(ConstraintVerdict.Violated::class.java)
        assertThat((verdict as ConstraintVerdict.Violated).code).isEqualTo("no_c1_c2")

        val assemblyComplete = PartialAssembly(listOf(Assignment(slotA, cand1)), complete = true)
        assertThat(constraint.evaluate(assemblyComplete, ""))
            .isEqualTo(ConstraintVerdict.Satisfied)
    }

    @Test
    fun `RequiredTogetherConstraint evaluation`() {
        val constraint = RequiredTogetherConstraint<String, String>(
            first = CandidateId("c1"),
            second = CandidateId("c2"),
            code = "c1_needs_c2"
        )

        val assemblyNone = PartialAssembly<String>(emptyList(), complete = false)
        assertThat(constraint.evaluate(assemblyNone, ""))
            .isEqualTo(ConstraintVerdict.Undetermined)

        val assemblyOne = PartialAssembly(listOf(Assignment(slotA, cand1)), complete = false)
        assertThat(constraint.evaluate(assemblyOne, ""))
            .isEqualTo(ConstraintVerdict.Undetermined)

        val assemblyBoth = PartialAssembly(
            listOf(Assignment(slotA, cand1), Assignment(slotB, cand2)),
            complete = false
        )
        assertThat(constraint.evaluate(assemblyBoth, ""))
            .isEqualTo(ConstraintVerdict.Satisfied)

        val assemblyOneComplete = PartialAssembly(listOf(Assignment(slotA, cand1)), complete = true)
        assertThat(constraint.evaluate(assemblyOneComplete, ""))
            .isInstanceOf(ConstraintVerdict.Violated::class.java)
    }

    @Test
    fun `AtomicGroupConstraint evaluation`() {
        val groupId = GroupId("g1")
        val gc1 = Candidate(CandidateId("c1"), "attr1", setOf(slotA), groupId)
        val gc2 = Candidate(CandidateId("c2"), "attr2", setOf(slotB), groupId)
        val gc3 = Candidate(CandidateId("c3"), "attr3", setOf(slotC), groupId)

        val constraint = AtomicGroupConstraint<String, String>(
            groupId = groupId,
            groupCandidates = setOf(gc1, gc2, gc3)
        )

        // Empty assembly - undetermined or satisfied
        val emptyAssembly = PartialAssembly<String>(emptyList(), complete = false)
        assertThat(constraint.evaluate(emptyAssembly, ""))
            .isEqualTo(ConstraintVerdict.Undetermined)

        val emptyAssemblyComplete = PartialAssembly<String>(emptyList(), complete = true)
        assertThat(constraint.evaluate(emptyAssemblyComplete, ""))
            .isEqualTo(ConstraintVerdict.Satisfied)

        // Partial assembly with gc1 selected, and slots B and C are not yet assigned.
        // It should be undetermined.
        val partialWithOne = PartialAssembly(listOf(Assignment(slotA, gc1)), complete = false)
        assertThat(constraint.evaluate(partialWithOne, ""))
            .isEqualTo(ConstraintVerdict.Undetermined)

        // Partial assembly with gc1 selected, but slot B is assigned to non-group candidate cand2.
        // Since gc2's only eligible slot is slotB, gc2 can never be added. This should violate.
        val partialBlocked = PartialAssembly(
            listOf(Assignment(slotA, gc1), Assignment(slotB, cand2)),
            complete = false
        )
        assertThat(constraint.evaluate(partialBlocked, ""))
            .isInstanceOf(ConstraintVerdict.Violated::class.java)

        // Complete assembly with all group candidates selected.
        val completeAll = PartialAssembly(
            listOf(Assignment(slotA, gc1), Assignment(slotB, gc2), Assignment(slotC, gc3)),
            complete = true
        )
        assertThat(constraint.evaluate(completeAll, ""))
            .isEqualTo(ConstraintVerdict.Satisfied)

        // Complete assembly with only gc1 selected (slots B and C filled with other candidates).
        val completePartial = PartialAssembly(
            listOf(Assignment(slotA, gc1), Assignment(slotB, cand2)),
            complete = true
        )
        assertThat(constraint.evaluate(completePartial, ""))
            .isInstanceOf(ConstraintVerdict.Violated::class.java)
    }

    @Test
    fun `AllowedCombinationConstraint evaluation`() {
        val c1 = CandidateId("c1")
        val c2 = CandidateId("c2")
        val c3 = CandidateId("c3")

        val constraint = AllowedCombinationConstraint<String, String>(
            allowedCombinations = listOf(
                setOf(c1, c2),
                setOf(c1, c3)
            )
        )

        // Empty assembly - undetermined
        val emptyAssembly = PartialAssembly<String>(emptyList(), complete = false)
        assertThat(constraint.evaluate(emptyAssembly, ""))
            .isEqualTo(ConstraintVerdict.Undetermined)

        // Subset of allowed combination - undetermined
        val assemblyOne = PartialAssembly(listOf(Assignment(slotA, cand1)), complete = false)
        assertThat(constraint.evaluate(assemblyOne, ""))
            .isEqualTo(ConstraintVerdict.Undetermined)

        // Valid complete combination
        val assemblyBoth = PartialAssembly(
            listOf(Assignment(slotA, cand1), Assignment(slotB, cand2)),
            complete = true
        )
        assertThat(constraint.evaluate(assemblyBoth, ""))
            .isEqualTo(ConstraintVerdict.Satisfied)

        // Invalid combination
        val assemblyInvalid = PartialAssembly(
            listOf(Assignment(slotB, cand2), Assignment(slotC, cand3)),
            complete = false
        )
        assertThat(constraint.evaluate(assemblyInvalid, ""))
            .isInstanceOf(ConstraintVerdict.Violated::class.java)
    }

    @Test
    fun `AttributePredicateConstraint rejects any invalid assignment in its slot`() {
        val second = Candidate(CandidateId("c4"), "wrong", setOf(slotA))
        val constraint = AttributePredicateConstraint<String, String>(slotA, "attr_check") { attr, context ->
            attr == context
        }
        val assembly = PartialAssembly(
            listOf(Assignment(slotA, cand1), Assignment(slotA, second)),
            complete = true
        )

        assertThat(constraint.evaluate(assembly, "attr1"))
            .isInstanceOf(ConstraintVerdict.Violated::class.java)
    }

    @Test
    fun `AttributePredicateConstraint edge cases`() {
        val constraint = AttributePredicateConstraint<String, String>(slotA, "code") { a, c -> true }
        // Empty and complete
        val emptyComplete = PartialAssembly<String>(emptyList(), complete = true)
        assertThat(constraint.evaluate(emptyComplete, ""))
            .isEqualTo(ConstraintVerdict.Satisfied)
    }

    @Test
    fun `RequiredTogetherConstraint edge cases`() {
        val constraint = RequiredTogetherConstraint<String, String>(
            first = CandidateId("c1"),
            second = CandidateId("c2")
        )
        // Neither present, incomplete
        val noneIncomplete = PartialAssembly<String>(emptyList(), complete = false)
        assertThat(constraint.evaluate(noneIncomplete, ""))
            .isEqualTo(ConstraintVerdict.Undetermined)

        // Neither present, complete
        val noneComplete = PartialAssembly<String>(emptyList(), complete = true)
        assertThat(constraint.evaluate(noneComplete, ""))
            .isEqualTo(ConstraintVerdict.Satisfied)
    }

    @Test
    fun `AtomicGroupConstraint group member not selected for active slots`() {
        val groupId = GroupId("g1")
        val gc1 = Candidate(CandidateId("c1"), "attr1", setOf(slotA), groupId)
        val gc2 = Candidate(CandidateId("c2"), "attr2", setOf(slotB), groupId)

        val constraint = AtomicGroupConstraint<String, String>(
            groupId = groupId,
            groupCandidates = setOf(gc1, gc2)
        )

        // Slot B is in activeSlots (it's assigned to cand2). gc2 is not selected.
        // This violates because member gc2 was not selected for active slots.
        val completeWithoutGc2 = PartialAssembly(
            listOf(Assignment(slotA, gc1), Assignment(slotB, cand2)),
            complete = true
        )
        assertThat(constraint.evaluate(completeWithoutGc2, ""))
            .isInstanceOf(ConstraintVerdict.Violated::class.java)
    }

    @Test
    fun `AllowedCombinationConstraint edge cases`() {
        val constraint = AllowedCombinationConstraint<String, String>(
            allowedCombinations = listOf(
                setOf(CandidateId("c1"), CandidateId("c2"))
            )
        )

        // Empty and complete
        val emptyComplete = PartialAssembly<String>(emptyList(), complete = true)
        assertThat(constraint.evaluate(emptyComplete, ""))
            .isEqualTo(ConstraintVerdict.Satisfied)

        // Complete but exact match fails (e.g. only c1 selected but complete is true)
        val onlyC1Complete = PartialAssembly(
            listOf(Assignment(slotA, cand1)),
            complete = true
        )
        assertThat(constraint.evaluate(onlyC1Complete, ""))
            .isInstanceOf(ConstraintVerdict.Violated::class.java)
    }
}
