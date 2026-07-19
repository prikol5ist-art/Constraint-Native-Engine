package io.github.constraintnative.fashion

import io.github.constraintnative.core.Assignment
import io.github.constraintnative.core.Candidate
import io.github.constraintnative.core.CandidateId
import io.github.constraintnative.core.ConstraintVerdict
import io.github.constraintnative.core.PartialAssembly
import io.github.constraintnative.core.SlotId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FashionPolicyTest {
    @Test
    fun `templates cover separates and dress outfits`() {
        assertThat(FashionTemplates.SEPARATES).containsExactly(
            FashionSlotRequirement(FashionSlots.TOP, 1, 1),
            FashionSlotRequirement(FashionSlots.BOTTOM, 1, 1),
            FashionSlotRequirement(FashionSlots.FOOTWEAR, 1, 1),
            FashionSlotRequirement(FashionSlots.OUTERWEAR, 0, 1),
            FashionSlotRequirement(FashionSlots.ACCESSORY, 0, 2)
        )
        assertThat(FashionTemplates.DRESS).containsExactly(
            FashionSlotRequirement(FashionSlots.FULL_BODY, 1, 1),
            FashionSlotRequirement(FashionSlots.FOOTWEAR, 1, 1),
            FashionSlotRequirement(FashionSlots.OUTERWEAR, 0, 1),
            FashionSlotRequirement(FashionSlots.ACCESSORY, 0, 2)
        )
    }

    @Test
    fun `availability constraint rejects laundry and unavailable candidates`() {
        val laundry = candidate(
            FashionCandidate(
                id = "laundry",
                name = "",
                type = "shirt",
                color = "blue",
                layer = 1,
                formalness = 0.5,
                category = "top",
                available = true,
                inLaundry = true
            ),
            FashionSlots.TOP
        )
        val unavailable = candidate(
            FashionCandidate(
                id = "unavailable",
                name = "",
                type = "shirt",
                color = "blue",
                layer = 1,
                formalness = 0.5,
                category = "top",
                available = false
            ),
            FashionSlots.TOP
        )
        val constraint = FashionAvailabilityConstraint()

        assertThat(constraint.evaluate(complete(laundry), context())).isInstanceOf(ConstraintVerdict.Violated::class.java)
        assertThat(constraint.evaluate(complete(unavailable), context())).isInstanceOf(ConstraintVerdict.Violated::class.java)
    }

    @Test
    fun `weather constraint never restores sandals in rain or light garments in cold`() {
        val sandals = candidate(
            FashionCandidate("sandals", "", "sandals", "tan", 1, 0.2, category = "footwear"),
            FashionSlots.FOOTWEAR
        )
        val shorts = candidate(
            FashionCandidate(
                "shorts",
                "",
                "shorts",
                "blue",
                1,
                0.2,
                category = "bottom",
                warmth = 2
            ),
            FashionSlots.BOTTOM
        )
        val constraint = FashionWeatherConstraint()

        assertThat(
            constraint.evaluate(complete(sandals), context(temperature = 12.0, precipitation = Precipitation.RAIN))
        ).isInstanceOf(ConstraintVerdict.Violated::class.java)
        assertThat(
            constraint.evaluate(complete(shorts), context(temperature = 2.0))
        ).isInstanceOf(ConstraintVerdict.Violated::class.java)
    }

    @Test
    fun `candidate preserves unknown optional values`() {
        val candidate = FashionCandidate("id", "", "shirt", "", 1, 0.5)

        assertThat(candidate.material).isNull()
        assertThat(candidate.silhouette).isNull()
        assertThat(candidate.warmth).isNull()
        assertThat(candidate.breathability).isNull()
        assertThat(candidate.lastWornAtEpochMillis).isNull()
    }

    private fun candidate(attributes: FashionCandidate, slot: SlotId) = Candidate(
        id = CandidateId(attributes.id),
        attributes = attributes,
        eligibleSlots = setOf(slot)
    )

    private fun complete(candidate: Candidate<FashionCandidate>) = PartialAssembly(
        assignments = listOf(Assignment(candidate.eligibleSlots.single(), candidate)),
        complete = true
    )

    private fun context(
        temperature: Double = 20.0,
        precipitation: Precipitation = Precipitation.NONE
    ) = FashionContext(temperature, precipitation, Occasion.CASUAL)
}
