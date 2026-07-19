package io.github.constraintnative.core

import kotlinx.serialization.Serializable

@Serializable
public sealed interface TraceEvent {
    public val sequence: Long
    public val message: String

    public companion object {
        public fun sanitizeMessage(msg: String): String {
            val singleLine = msg.replace('\r', ' ').replace('\n', ' ')
            return if (singleLine.length <= 512) {
                singleLine
            } else {
                singleLine.take(512)
            }
        }

        private fun requireTraceText(value: String, field: String): String {
            require(value.none { it == '\n' || it == '\r' }) { "$field must be single-line" }
            require(value.length <= 512) { "$field must be at most 512 characters" }
            return value
        }
    }

    @Serializable
    public data class CandidateRejected(
        override val sequence: Long,
        val candidateId: CandidateId,
        val slotId: SlotId,
        val reason: String
    ) : TraceEvent {
        init { requireTraceText(reason, "reason") }
        override val message: String = sanitizeMessage("Candidate ${candidateId.value} rejected for slot ${slotId.value}: $reason")
    }

    @Serializable
    public data class PartialAssemblyPruned(
        override val sequence: Long,
        val slotId: SlotId,
        val reason: String
    ) : TraceEvent {
        init { requireTraceText(reason, "reason") }
        override val message: String = sanitizeMessage("Partial assembly pruned at slot ${slotId.value}: $reason")
    }

    @Serializable
    public data class SolutionAccepted(
        override val sequence: Long,
        val solutionId: SolutionId,
        val score: Double
    ) : TraceEvent {
        override val message: String = sanitizeMessage("Solution ${solutionId.value} accepted with score $score")
    }

    @Serializable
    public data class SearchStopped(
        override val sequence: Long,
        val reason: String
    ) : TraceEvent {
        init { requireTraceText(reason, "reason") }
        override val message: String = sanitizeMessage("Search stopped: $reason")
    }
}
