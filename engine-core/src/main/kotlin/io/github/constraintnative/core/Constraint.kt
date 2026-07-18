package io.github.constraintnative.core

import kotlinx.serialization.Serializable

@Serializable
public sealed interface ConstraintVerdict {
    @Serializable
    public data object Satisfied : ConstraintVerdict

    @Serializable
    public data object Undetermined : ConstraintVerdict

    @Serializable
    public data class Violated(
        val code: String,
        val message: String
    ) : ConstraintVerdict {
        init {
            val regex = Regex("^[a-z0-9_.-]{1,80}$")
            require(regex.matches(code)) {
                "Violation code must match [a-z0-9_.-]{1,80}, got: '$code'"
            }
        }
    }
}

public fun interface HardConstraint<A, C> {
    public fun evaluate(assembly: PartialAssembly<A>, context: C): ConstraintVerdict
}
