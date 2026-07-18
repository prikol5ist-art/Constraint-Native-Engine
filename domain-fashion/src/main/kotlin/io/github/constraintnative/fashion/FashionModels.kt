package io.github.constraintnative.fashion

import io.github.constraintnative.core.SlotId

public object FashionSlots {
    public val TOP: SlotId = SlotId("top")
    public val BOTTOM: SlotId = SlotId("bottom")
    public val OUTERWEAR: SlotId = SlotId("outerwear")
    public val FOOTWEAR: SlotId = SlotId("footwear")
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
    val occasion: Occasion
)

public data class FashionCandidate(
    val id: String,
    val name: String,
    val type: String,
    val color: String,
    val layer: Int,
    val formalness: Double
)
