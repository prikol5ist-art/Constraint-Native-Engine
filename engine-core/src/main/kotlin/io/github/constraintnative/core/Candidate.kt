package io.github.constraintnative.core

public data class Candidate<A>(
    val id: CandidateId,
    val attributes: A,
    val eligibleSlots: Set<SlotId>,
    val groupId: GroupId? = null
) {
    init {
        require(eligibleSlots.isNotEmpty()) { "Candidate must be eligible for at least one slot" }
    }
}

public data class SlotRequirement(
    val slot: SlotId,
    val min: Int,
    val max: Int
) {
    init {
        require(min >= 0) { "min must be non-negative" }
        require(max >= min) { "max must be greater than or equal to min" }
    }
}

public data class Assignment<A>(
    val slot: SlotId,
    val candidate: Candidate<A>
) {
    init {
        require(slot in candidate.eligibleSlots) { "Candidate ${candidate.id.value} is not eligible for slot ${slot.value}" }
    }
}

public data class PartialAssembly<A>(
    val assignments: List<Assignment<A>>,
    val complete: Boolean
) {
    init {
        val uniqueCandidates = assignments.map { it.candidate.id }.toSet()
        require(uniqueCandidates.size == assignments.size) { "Candidates in an assembly must be unique" }
    }
}
