package io.github.constraintnative.fashion

import io.github.constraintnative.core.FeasibleSolution
import io.github.constraintnative.generative.ContextRenderer

public class FashionContextRenderer : ContextRenderer<FashionCandidate, FashionContext> {
    override fun renderContext(context: FashionContext): String {
        return "Context: temp=${context.temperatureCelsius}°C, precipitation=${context.precipitation}, occasion=${context.occasion}"
    }

    override fun renderSolution(solution: FeasibleSolution<FashionCandidate>): String {
        val sorted = solution.assignments.sortedBy { it.slot.value }
        return sorted.joinToString(separator = "; ") { assignment ->
            val c = assignment.candidate.attributes
            "${assignment.slot.value}: ${c.name} (${c.type}, color=${c.color}, layer=${c.layer}, formalness=${c.formalness})"
        }
    }
}
