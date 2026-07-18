package io.github.constraintnative.core

import kotlinx.serialization.Serializable

private fun validateId(value: String): String {
    require(value.isNotBlank()) { "ID must not be blank" }
    require(value.trim() == value) { "ID must be trimmed" }
    require(value.length <= 128) { "ID must be at most 128 characters long" }
    require(value.none { it.isISOControl() }) { "ID must not contain control characters" }
    return value
}

@Serializable
@JvmInline
public value class CandidateId(public val value: String) {
    init {
        validateId(value)
    }
}

@Serializable
@JvmInline
public value class SlotId(public val value: String) {
    init {
        validateId(value)
    }
}

@Serializable
@JvmInline
public value class GroupId(public val value: String) {
    init {
        validateId(value)
    }
}

@Serializable
@JvmInline
public value class SolutionId(public val value: String) {
    init {
        validateId(value)
    }
}
