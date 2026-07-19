package io.github.constraintnative.examples.wardrobe

import io.github.constraintnative.fashion.Occasion

public data class CliOptions(
    val occasion: Occasion = Occasion.CASUAL,
    val temperatureCelsius: Double = 20.0,
    val diversityWeight: Double = 0.0
)

public sealed interface CliParseResult {
    public data class Valid(val options: CliOptions) : CliParseResult
    public data class Invalid(val message: String) : CliParseResult
}

public fun parseCliArguments(args: Array<String>): CliParseResult {
    var occasion = Occasion.CASUAL
    var temperature = 20.0
    var diversityWeight = 0.0
    var index = 0
    while (index < args.size) {
        val argument = args[index]
        val (name, rawValue, consumedNext) = when {
            argument.contains('=') -> Triple(argument.substringBefore('='), argument.substringAfter('='), false)
            argument in setOf("--occasion", "--temperature", "--diversityWeight") -> {
                if (index + 1 >= args.size) return CliParseResult.Invalid("Missing value for $argument")
                Triple(argument, args[index + 1], true)
            }
            else -> return CliParseResult.Invalid("Unknown argument: $argument")
        }
        when (name) {
            "--occasion" -> occasion = when (rawValue.lowercase()) {
                "casual" -> Occasion.CASUAL
                "formal" -> Occasion.FORMAL
                "sporty" -> Occasion.SPORTY
                "business" -> Occasion.BUSINESS
                else -> return CliParseResult.Invalid("--occasion must be casual, formal, sporty, or business")
            }
            "--temperature" -> temperature = rawValue.toDoubleOrNull()
                ?.takeIf(Double::isFinite)
                ?: return CliParseResult.Invalid("--temperature must be finite")
            "--diversityWeight" -> diversityWeight = rawValue.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in 0.0..1.0 }
                ?: return CliParseResult.Invalid("--diversityWeight must be in 0.0..1.0")
            else -> return CliParseResult.Invalid("Unknown argument: $name")
        }
        index += if (consumedNext) 2 else 1
    }
    return CliParseResult.Valid(CliOptions(occasion, temperature, diversityWeight))
}
