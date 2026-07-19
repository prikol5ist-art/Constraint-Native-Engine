package io.github.constraintnative.fashion

import io.github.constraintnative.core.SlotId

public object FashionSlots {
    public val TOP: SlotId = SlotId("top")
    public val BOTTOM: SlotId = SlotId("bottom")
    public val OUTERWEAR: SlotId = SlotId("outerwear")
    public val FOOTWEAR: SlotId = SlotId("footwear")
    public val FULL_BODY: SlotId = SlotId("full_body")
    public val ACCESSORY: SlotId = SlotId("accessory")
}

public data class FashionSlotRequirement(
    val slot: SlotId,
    val min: Int,
    val max: Int
) {
    init {
        require(min >= 0) { "min must be non-negative" }
        require(max >= min) { "max must be greater than or equal to min" }
    }
}

public object FashionTemplates {
    public val SEPARATES: List<FashionSlotRequirement> = listOf(
        FashionSlotRequirement(FashionSlots.TOP, 1, 1),
        FashionSlotRequirement(FashionSlots.BOTTOM, 1, 1),
        FashionSlotRequirement(FashionSlots.FOOTWEAR, 1, 1),
        FashionSlotRequirement(FashionSlots.OUTERWEAR, 0, 1),
        FashionSlotRequirement(FashionSlots.ACCESSORY, 0, 2)
    )

    public val DRESS: List<FashionSlotRequirement> = listOf(
        FashionSlotRequirement(FashionSlots.FULL_BODY, 1, 1),
        FashionSlotRequirement(FashionSlots.FOOTWEAR, 1, 1),
        FashionSlotRequirement(FashionSlots.OUTERWEAR, 0, 1),
        FashionSlotRequirement(FashionSlots.ACCESSORY, 0, 2)
    )
}

public enum class Occasion {
    CASUAL,
    FORMAL,
    SPORTY,
    BUSINESS
}

public enum class Precipitation {
    NONE,
    RAIN,
    SNOW
}

public data class FashionContext(
    val temperatureCelsius: Double,
    val precipitation: Precipitation,
    val occasion: Occasion,
    val windMetersPerSecond: Double = 0.0,
    val targetFormalness: ClosedFloatingPointRange<Double>? = null,
    val preferredStyleClusters: Set<String> = emptySet(),
    val avoidedColors: Set<String> = emptySet(),
    val currentTimeEpochMillis: Long = 0L
) {
    init {
        require(temperatureCelsius.isFinite()) { "temperatureCelsius must be finite" }
        require(windMetersPerSecond.isFinite() && windMetersPerSecond >= 0.0) {
            "windMetersPerSecond must be finite and non-negative"
        }
        targetFormalness?.let {
            require(it.start.isFinite() && it.endInclusive.isFinite()) { "targetFormalness must be finite" }
            require(it.start in 0.0..1.0 && it.endInclusive in 0.0..1.0 && it.start <= it.endInclusive) {
                "targetFormalness must be within 0.0..1.0"
            }
        }
    }
}

public data class FashionCandidate(
    val id: String,
    val name: String,
    val type: String,
    val color: String,
    val layer: Int,
    val formalness: Double,
    val category: String? = null,
    val material: String? = null,
    val silhouette: String? = null,
    val warmth: Int? = null,
    val breathability: Int? = null,
    val styleScores: Map<String, Double> = emptyMap(),
    val available: Boolean = true,
    val inLaundry: Boolean = false,
    val lastWornAtEpochMillis: Long? = null,
    val wearCount: Int = 0,
    val favorite: Boolean = false,
    val lockedGroupId: String? = null
) {
    init {
        require(formalness.isFinite() && formalness in 0.0..1.0) { "formalness must be in 0.0..1.0" }
        require(layer >= 0) { "layer must be non-negative" }
        warmth?.let { require(it in 1..10) { "warmth must be in 1..10" } }
        breathability?.let { require(it in 1..10) { "breathability must be in 1..10" } }
        require(styleScores.values.all { it.isFinite() }) { "style scores must be finite" }
        require(wearCount >= 0) { "wearCount must be non-negative" }
    }
}
