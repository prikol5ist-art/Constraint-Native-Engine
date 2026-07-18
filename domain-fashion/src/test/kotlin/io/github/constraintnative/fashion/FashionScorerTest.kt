package io.github.constraintnative.fashion

import io.github.constraintnative.core.Assignment
import io.github.constraintnative.core.Candidate
import io.github.constraintnative.core.CandidateId
import io.github.constraintnative.core.PartialAssembly
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FashionScorerTest {

    private val topRed = Candidate(
        id = CandidateId("topRed"),
        attributes = FashionCandidate("topRed", "Red Shirt", "Shirt", "red", 1, 0.4),
        eligibleSlots = setOf(FashionSlots.TOP)
    )

    private val topOrange = Candidate(
        id = CandidateId("topOrange"),
        attributes = FashionCandidate("topOrange", "Orange Shirt", "Shirt", "orange", 1, 0.4),
        eligibleSlots = setOf(FashionSlots.TOP)
    )

    private val topNeutral = Candidate(
        id = CandidateId("topNeutral"),
        attributes = FashionCandidate("topNeutral", "Black Shirt", "Shirt", "black", 1, 0.4),
        eligibleSlots = setOf(FashionSlots.TOP)
    )

    private val outerRed = Candidate(
        id = CandidateId("outerRed"),
        attributes = FashionCandidate("outerRed", "Red Jacket", "Jacket", "red", 2, 0.3),
        eligibleSlots = setOf(FashionSlots.OUTERWEAR)
    )

    private val outerOrange = Candidate(
        id = CandidateId("outerOrange"),
        attributes = FashionCandidate("outerOrange", "Orange Jacket", "Jacket", "orange", 2, 0.3),
        eligibleSlots = setOf(FashionSlots.OUTERWEAR)
    )

    private val outerLowLayer = Candidate(
        id = CandidateId("outerLowLayer"),
        attributes = FashionCandidate("outerLowLayer", "Violating Jacket", "Jacket", "black", 1, 0.3),
        eligibleSlots = setOf(FashionSlots.OUTERWEAR)
    )

    private val defaultContext = FashionContext(
        temperatureCelsius = 20.0,
        precipitation = Precipitation.NONE,
        occasion = Occasion.CASUAL
    )

    @Test
    fun `ColorHarmonyScorer should penalize red-orange clash and award accent-neutral bonus`() {
        val scorer = ColorHarmonyScorer()

        // 1. Red + Orange clash
        val clashAssignments = listOf(
            Assignment(FashionSlots.TOP, topRed),
            Assignment(FashionSlots.OUTERWEAR, outerOrange)
        )
        val clashScore = scorer.score(PartialAssembly(clashAssignments, true), defaultContext)
        // Red & Orange clash penalty (-15.0). No neutrals present (Red and Orange are accents), so no bonus. Total: -15.0
        assertThat(clashScore.value).isEqualTo(-15.0)

        // 2. Accent + Neutral bonus
        val harmoniousAssignments = listOf(
            Assignment(FashionSlots.TOP, topRed),
            Assignment(FashionSlots.OUTERWEAR, outerLowLayer) // black is neutral
        )
        val harmoniousScore = scorer.score(PartialAssembly(harmoniousAssignments, true), defaultContext)
        // No clash. 1 neutral (black) and 1 accent (red). Bonus: +10.0
        assertThat(harmoniousScore.value).isEqualTo(10.0)
    }

    @Test
    fun `LayerScorer should penalize outerwear with layer less than or equal to top`() {
        val scorer = LayerScorer()

        // 1. Valid layer order (top layer 1, outer layer 2)
        val valid = listOf(
            Assignment(FashionSlots.TOP, topRed),
            Assignment(FashionSlots.OUTERWEAR, outerRed)
        )
        assertThat(scorer.score(PartialAssembly(valid, true), defaultContext).value).isEqualTo(0.0)

        // 2. Invalid layer order (top layer 1, outer layer 1)
        val invalid = listOf(
            Assignment(FashionSlots.TOP, topRed),
            Assignment(FashionSlots.OUTERWEAR, outerLowLayer)
        )
        assertThat(scorer.score(PartialAssembly(invalid, true), defaultContext).value).isEqualTo(-50.0)
    }

    @Test
    fun `TemperatureScorer should penalize missing outerwear in cold and outerwear presence in hot weather`() {
        val scorer = TemperatureScorer()

        // Cold weather (10 deg C) without outerwear
        val coldNoOuter = FashionContext(10.0, Precipitation.NONE, Occasion.CASUAL)
        val assign1 = listOf(Assignment(FashionSlots.TOP, topRed))
        assertThat(scorer.score(PartialAssembly(assign1, true), coldNoOuter).value).isEqualTo(-20.0)

        // Hot weather (30 deg C) with outerwear
        val hotWithOuter = FashionContext(30.0, Precipitation.NONE, Occasion.CASUAL)
        val assign2 = listOf(
            Assignment(FashionSlots.TOP, topRed),
            Assignment(FashionSlots.OUTERWEAR, outerRed)
        )
        assertThat(scorer.score(PartialAssembly(assign2, true), hotWithOuter).value).isEqualTo(-30.0)
    }

    @Test
    fun `OccasionScorer should penalize low formalness on formal occasion`() {
        val scorer = OccasionScorer()
        val formalContext = FashionContext(20.0, Precipitation.NONE, Occasion.FORMAL)

        val lowFormalness = listOf(
            Assignment(FashionSlots.TOP, topRed), // formalness 0.4
            Assignment(FashionSlots.OUTERWEAR, outerRed) // formalness 0.3
        )
        // Average formalness = 0.35. Threshold = 0.7. Diff = 0.35 * -50.0 = -17.5
        assertThat(scorer.score(PartialAssembly(lowFormalness, true), formalContext).value).isEqualTo(-17.5)
    }
}
