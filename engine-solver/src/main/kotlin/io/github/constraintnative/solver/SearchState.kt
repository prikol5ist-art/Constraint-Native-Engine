package io.github.constraintnative.solver

import io.github.constraintnative.core.Assignment
import io.github.constraintnative.core.CandidateId
import io.github.constraintnative.core.SlotId

internal data class SearchState<A>(
    val slotIndex: Int,
    val assignments: List<Assignment<A>>,
    val selectedIds: Set<CandidateId>,
    val lastCandidateIndex: Int,
    val slotCounts: Map<SlotId, Int>
)
