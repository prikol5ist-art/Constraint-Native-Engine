package io.github.constraintnative.solver

import io.github.constraintnative.core.Assignment
import io.github.constraintnative.core.SolutionId
import java.security.MessageDigest

public object SolutionCanonicalizer {
    public fun <A> canonicalize(assignments: List<Assignment<A>>): SolutionId {
        val records = assignments.map { "${it.slot.value}=${it.candidate.id.value}\n" }
        val sortedRecords = records.sorted()
        val concatenated = sortedRecords.joinToString("")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(concatenated.toByteArray(Charsets.UTF_8))
        val truncatedBytes = hashBytes.take(16)
        val hexString = truncatedBytes.joinToString("") { "%02x".format(it) }
        return SolutionId(hexString)
    }
}
