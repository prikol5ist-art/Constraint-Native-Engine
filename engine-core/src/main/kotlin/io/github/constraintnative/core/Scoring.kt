package io.github.constraintnative.core

import kotlinx.serialization.Serializable

@Serializable
public data class ScoreComponent(
    val name: String,
    val value: Double
) {
    init {
        require(value.isFinite()) { "Score value must be finite, got: $value" }
    }
}

public fun interface SoftScorer<A, C> {
    public fun score(assembly: PartialAssembly<A>, context: C): ScoreComponent
}
