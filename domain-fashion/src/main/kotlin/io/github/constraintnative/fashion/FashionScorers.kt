package io.github.constraintnative.fashion

import io.github.constraintnative.core.Assignment
import io.github.constraintnative.core.PartialAssembly
import io.github.constraintnative.core.ScoreComponent
import io.github.constraintnative.core.SoftScorer

public class TemperatureScorer : SoftScorer<FashionCandidate, FashionContext> {
    override fun score(
        assembly: PartialAssembly<FashionCandidate>,
        context: FashionContext
    ): ScoreComponent {
        var penalty = 0.0
        val assignments = assembly.assignments
        val hasOuterwear = assignments.any { it.slot == FashionSlots.OUTERWEAR }
        val temp = context.temperatureCelsius

        if (temp < 15.0 && !hasOuterwear) {
            penalty -= 20.0
        }
        if (temp > 25.0 && hasOuterwear) {
            penalty -= 30.0
        }
        return ScoreComponent("temperature_match", penalty)
    }
}

public class OccasionScorer : SoftScorer<FashionCandidate, FashionContext> {
    override fun score(
        assembly: PartialAssembly<FashionCandidate>,
        context: FashionContext
    ): ScoreComponent {
        val assignments = assembly.assignments
        if (assignments.isEmpty()) return ScoreComponent("occasion_match", 0.0)

        val avgFormalness = assignments.map { it.candidate.attributes.formalness }.average()
        var diff = 0.0
        when (context.occasion) {
            Occasion.FORMAL -> {
                if (avgFormalness < 0.7) {
                    diff = (0.7 - avgFormalness) * -50.0
                }
            }
            Occasion.CASUAL -> {
                if (avgFormalness > 0.4) {
                    diff = (avgFormalness - 0.4) * -30.0
                }
            }
            Occasion.SPORTY -> {
                if (avgFormalness > 0.3) {
                    diff = (avgFormalness - 0.3) * -40.0
                }
            }
            Occasion.BUSINESS -> {
                if (avgFormalness < 0.5) {
                    diff = (0.5 - avgFormalness) * -40.0
                }
            }
        }
        return ScoreComponent("occasion_match", diff)
    }
}

public class LayerScorer : SoftScorer<FashionCandidate, FashionContext> {
    override fun score(
        assembly: PartialAssembly<FashionCandidate>,
        context: FashionContext
    ): ScoreComponent {
        var penalty = 0.0
        val assignments = assembly.assignments
        val top = assignments.find { it.slot == FashionSlots.TOP }
        val outer = assignments.find { it.slot == FashionSlots.OUTERWEAR }

        if (top != null && outer != null) {
            val topLayer = top.candidate.attributes.layer
            val outerLayer = outer.candidate.attributes.layer
            if (outerLayer <= topLayer) {
                penalty -= 50.0
            }
        }
        return ScoreComponent("layer_order", penalty)
    }
}

public class ColorHarmonyScorer : SoftScorer<FashionCandidate, FashionContext> {
    override fun score(
        assembly: PartialAssembly<FashionCandidate>,
        context: FashionContext
    ): ScoreComponent {
        val assignments = assembly.assignments
        val colors = assignments.map { it.candidate.attributes.color.lowercase() }
        var score = 0.0

        if ("red" in colors && "orange" in colors) {
            score -= 15.0
        }

        val neutrals = setOf("black", "grey", "white", "navy", "brown")
        val neutralsCount = colors.count { it in neutrals }
        val accentsCount = colors.size - neutralsCount

        if (neutralsCount > 0 && accentsCount > 0) {
            score += 10.0
        }

        return ScoreComponent("color_harmony", score)
    }
}
