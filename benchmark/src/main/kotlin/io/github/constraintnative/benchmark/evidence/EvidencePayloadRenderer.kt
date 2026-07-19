package io.github.constraintnative.benchmark.evidence

import io.github.constraintnative.core.Candidate
import io.github.constraintnative.core.FeasibleSolution
import io.github.constraintnative.fashion.FashionCandidate
import io.github.constraintnative.fashion.FashionContext

public object EvidencePayloadRenderer {

    public fun inventoryPayload(candidates: List<Candidate<FashionCandidate>>, context: FashionContext): String {
        return buildString {
            append("context=temperature:${context.temperatureCelsius};precipitation:${context.precipitation};occasion:${context.occasion}\n")
            candidates.sortedBy { it.id.value }.forEach { c ->
                append("id=${c.id.value};category=${c.attributes.category};type=${c.attributes.type};color=${c.attributes.color};material=${c.attributes.material};silhouette=${c.attributes.silhouette};formality=${c.attributes.formalness};warmth=${c.attributes.warmth};breathability=${c.attributes.breathability};styles=${c.attributes.styleScores.entries.joinToString(",") { "${it.key}:${it.value}" }}\n")
            }
        }
    }

    public fun solutionsPayload(solutions: List<FeasibleSolution<FashionCandidate>>): String {
        return buildString {
            solutions.sortedBy { it.id.value }.forEach { sol ->
                val sortedAssignments = sol.assignments.sortedBy { it.slot.value }
                append("solution=${sol.id.value};score=${sol.totalScore};assignments=${sortedAssignments.joinToString(",") { "${it.slot.value}:${it.candidate.id.value}" }}\n")
            }
        }
    }

    public fun utf8Bytes(payload: String): Int {
        return payload.toByteArray(Charsets.UTF_8).size
    }
}
