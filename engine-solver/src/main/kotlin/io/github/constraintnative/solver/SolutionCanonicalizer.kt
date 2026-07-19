package io.github.constraintnative.solver

import io.github.constraintnative.core.Assignment
import io.github.constraintnative.core.SolutionId
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

public object SolutionCanonicalizer {
    private const val FORMAT_VERSION: Byte = 1

    private fun writeString(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    public fun <A> canonicalize(assignments: List<Assignment<A>>): SolutionId {
        val ordered = assignments.sortedWith(
            compareBy<Assignment<A>> { it.slot.value }.thenBy { it.candidate.id.value }
        )
        val encoded = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeByte(FORMAT_VERSION.toInt())
                output.writeInt(ordered.size)
                ordered.forEach { assignment ->
                    writeString(output, assignment.slot.value)
                    writeString(output, assignment.candidate.id.value)
                }
            }
            bytes.toByteArray()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
        val truncatedBytes = digest.take(16)
        val hexString = truncatedBytes.joinToString("") { "%02x".format(it) }
        return SolutionId(hexString)
    }
}
