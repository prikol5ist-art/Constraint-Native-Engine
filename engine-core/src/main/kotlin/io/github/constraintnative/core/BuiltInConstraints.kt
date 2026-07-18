package io.github.constraintnative.core

public class AttributePredicateConstraint<A, C>(
    public val slot: SlotId,
    public val code: String,
    public val predicate: (A, C) -> Boolean
) : HardConstraint<A, C> {
    override fun evaluate(assembly: PartialAssembly<A>, context: C): ConstraintVerdict {
        val assignment = assembly.assignments.find { it.slot == slot }
            ?: return ConstraintVerdict.Undetermined
        return if (predicate(assignment.candidate.attributes, context)) {
            ConstraintVerdict.Satisfied
        } else {
            ConstraintVerdict.Violated(
                code,
                "Candidate ${assignment.candidate.id.value} does not satisfy attribute predicate for slot ${slot.value}"
            )
        }
    }
}

public class ForbiddenPairConstraint<A, C>(
    public val first: CandidateId,
    public val second: CandidateId,
    public val code: String = "forbidden_pair"
) : HardConstraint<A, C> {
    override fun evaluate(assembly: PartialAssembly<A>, context: C): ConstraintVerdict {
        val hasFirst = assembly.assignments.any { it.candidate.id == first }
        val hasSecond = assembly.assignments.any { it.candidate.id == second }
        return if (hasFirst && hasSecond) {
            ConstraintVerdict.Violated(
                code,
                "Candidates ${first.value} and ${second.value} cannot be selected together"
            )
        } else {
            if (assembly.complete) ConstraintVerdict.Satisfied else ConstraintVerdict.Undetermined
        }
    }
}

public class RequiredTogetherConstraint<A, C>(
    public val first: CandidateId,
    public val second: CandidateId,
    public val code: String = "required_together"
) : HardConstraint<A, C> {
    override fun evaluate(assembly: PartialAssembly<A>, context: C): ConstraintVerdict {
        val hasFirst = assembly.assignments.any { it.candidate.id == first }
        val hasSecond = assembly.assignments.any { it.candidate.id == second }
        return when {
            hasFirst && hasSecond -> ConstraintVerdict.Satisfied
            !hasFirst && !hasSecond -> if (assembly.complete) ConstraintVerdict.Satisfied else ConstraintVerdict.Undetermined
            assembly.complete -> {
                val missing = if (hasFirst) second else first
                val present = if (hasFirst) first else second
                ConstraintVerdict.Violated(
                    code,
                    "Candidate ${present.value} requires ${missing.value} to be selected"
                )
            }
            else -> ConstraintVerdict.Undetermined
        }
    }
}

public class AtomicGroupConstraint<A, C>(
    public val groupId: GroupId,
    public val groupCandidates: Set<Candidate<A>>,
    public val code: String = "atomic_group"
) : HardConstraint<A, C> {
    override fun evaluate(assembly: PartialAssembly<A>, context: C): ConstraintVerdict {
        val selectedFromGroup = assembly.assignments.filter { it.candidate.groupId == groupId }
        if (selectedFromGroup.isEmpty()) {
            return if (assembly.complete) ConstraintVerdict.Satisfied else ConstraintVerdict.Undetermined
        }

        val selectedIds = selectedFromGroup.map { it.candidate.id }.toSet()
        val activeSlots = assembly.assignments.map { it.slot }.toSet()

        for (g in groupCandidates) {
            if (g.id !in selectedIds) {
                val slotsFilledByNonGroup = assembly.assignments
                    .filter { it.candidate.groupId != groupId }
                    .map { it.slot }
                    .toSet()

                val allEligibleSlotsBlocked = g.eligibleSlots.all { it in slotsFilledByNonGroup }
                if (allEligibleSlotsBlocked) {
                    return ConstraintVerdict.Violated(
                        code,
                        "Atomic group ${groupId.value} violated: member ${g.id.value} cannot be assigned to any eligible slot"
                    )
                }
            }
        }

        return if (assembly.complete) {
            for (g in groupCandidates) {
                if (g.id !in selectedIds) {
                    val hasCompatibleSlotInAssembly = g.eligibleSlots.any { it in activeSlots }
                    if (hasCompatibleSlotInAssembly) {
                        return ConstraintVerdict.Violated(
                            code,
                            "Atomic group ${groupId.value} violated: member ${g.id.value} was not selected for active slots"
                        )
                    }
                }
            }
            ConstraintVerdict.Satisfied
        } else {
            ConstraintVerdict.Undetermined
        }
    }
}

public class AllowedCombinationConstraint<A, C>(
    public val allowedCombinations: List<Set<CandidateId>>,
    public val code: String = "allowed_combination"
) : HardConstraint<A, C> {
    override fun evaluate(assembly: PartialAssembly<A>, context: C): ConstraintVerdict {
        val selectedIds = assembly.assignments.map { it.candidate.id }.toSet()
        if (selectedIds.isEmpty()) {
            return if (assembly.complete) ConstraintVerdict.Satisfied else ConstraintVerdict.Undetermined
        }

        val potentialMatches = allowedCombinations.filter { allowed ->
            selectedIds.all { it in allowed }
        }

        if (potentialMatches.isEmpty()) {
            return ConstraintVerdict.Violated(
                code,
                "Selected candidates do not form a valid subset of any allowed combination"
            )
        }

        return if (assembly.complete) {
            val exactMatch = potentialMatches.any { it == selectedIds }
            if (exactMatch) ConstraintVerdict.Satisfied else ConstraintVerdict.Violated(
                code,
                "Selected candidates do not match any allowed combination exactly"
            )
        } else {
            ConstraintVerdict.Undetermined
        }
    }
}
