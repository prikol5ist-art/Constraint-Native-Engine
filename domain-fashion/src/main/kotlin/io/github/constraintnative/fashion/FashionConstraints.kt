package io.github.constraintnative.fashion

import io.github.constraintnative.core.ConstraintVerdict
import io.github.constraintnative.core.HardConstraint
import io.github.constraintnative.core.PartialAssembly

public class FashionAvailabilityConstraint : HardConstraint<FashionCandidate, FashionContext> {
    override fun evaluate(
        assembly: PartialAssembly<FashionCandidate>,
        context: FashionContext
    ): ConstraintVerdict {
        val unavailable = assembly.assignments.firstOrNull {
            !it.candidate.attributes.available || it.candidate.attributes.inLaundry
        } ?: return if (assembly.complete) ConstraintVerdict.Satisfied else ConstraintVerdict.Undetermined

        return ConstraintVerdict.Violated(
            code = "fashion.availability",
            message = "Candidate ${unavailable.candidate.id.value} is unavailable"
        )
    }
}

public class FashionWeatherConstraint : HardConstraint<FashionCandidate, FashionContext> {
    override fun evaluate(
        assembly: PartialAssembly<FashionCandidate>,
        context: FashionContext
    ): ConstraintVerdict {
        val invalid = assembly.assignments.firstOrNull { assignment ->
            !isWeatherSuitable(assignment.candidate.attributes, context)
        } ?: return if (assembly.complete) ConstraintVerdict.Satisfied else ConstraintVerdict.Undetermined

        return ConstraintVerdict.Violated(
            code = "fashion.weather",
            message = "Candidate ${invalid.candidate.id.value} is not suitable for current weather"
        )
    }

    private fun isWeatherSuitable(candidate: FashionCandidate, context: FashionContext): Boolean {
        val type = candidate.type.lowercase()
        val category = candidate.category?.lowercase()
        val material = candidate.material?.lowercase()
        val warmth = candidate.warmth

        if (context.precipitation == Precipitation.RAIN && type == "sandals") return false
        if (context.precipitation == Precipitation.SNOW && type != "boots" && category == "footwear") return false
        if (context.temperatureCelsius < 5.0 && type == "shorts") return false
        if (context.temperatureCelsius < 5.0 && category == "full_body" && warmth != null && warmth <= 4) return false
        if (context.temperatureCelsius > 20.0 && category == "top" && material in HEAVY_MATERIALS) return false
        if (context.temperatureCelsius > 25.0 && category == "bottom" && (warmth ?: 0) >= 6) return false
        if (context.temperatureCelsius > 25.0 && category == "full_body" && (warmth ?: 0) >= 7) return false
        if (context.temperatureCelsius > 20.0 && category == "outerwear") return false

        return true
    }

    private companion object {
        val HEAVY_MATERIALS: Set<String> = setOf("wool", "fleece", "leather", "suede", "velvet")
    }
}
