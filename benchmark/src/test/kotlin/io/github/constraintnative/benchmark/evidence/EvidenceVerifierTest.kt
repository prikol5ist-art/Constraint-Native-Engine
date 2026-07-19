package io.github.constraintnative.benchmark.evidence

import io.github.constraintnative.core.*
import io.github.constraintnative.fashion.*
import io.github.constraintnative.solver.SolutionCanonicalizer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EvidenceVerifierTest {

    private val candidates = SyntheticFashionInventory.create(25, 20260719L)
    private val candidateMap = candidates.associateBy { it.id }

    private val context = FashionContext(
        temperatureCelsius = 12.0,
        precipitation = Precipitation.RAIN,
        occasion = Occasion.CASUAL
    )

    private val slots = listOf(
        SlotRequirement(FashionSlots.TOP, min = 1, max = 1),
        SlotRequirement(FashionSlots.BOTTOM, min = 1, max = 1),
        SlotRequirement(FashionSlots.FOOTWEAR, min = 1, max = 1)
    )

    private val hardConstraints = listOf(
        FashionAvailabilityConstraint(),
        FashionWeatherConstraint()
    )

    private fun getCandidate(idStr: String): Candidate<FashionCandidate> {
        return candidateMap.values.first { it.id.value == idStr }
    }

    @Test
    fun `verifySolutions reports zero violations for valid solution`() {
        val top = getCandidate("item-0000") // TOP
        val bottom = getCandidate("item-0006") // BOTTOM
        val footwear = getCandidate("item-0014") // FOOTWEAR (sneakers)

        val assignments = listOf(
            Assignment(FashionSlots.TOP, top),
            Assignment(FashionSlots.BOTTOM, bottom),
            Assignment(FashionSlots.FOOTWEAR, footwear)
        )
        val rawId = SolutionCanonicalizer.canonicalize(assignments)
        val solution = FeasibleSolution(rawId, assignments, emptyList(), 10.0)

        val violations = EvidenceVerifier.verifySolutions(
            solutions = listOf(solution),
            knownCandidates = candidates,
            requirements = slots,
            hardConstraints = hardConstraints,
            context = context
        )

        assertThat(violations).isZero()
    }

    @Test
    fun `verifySolutions detects hard constraint violation`() {
        // Create an unavailable candidate
        val top = getCandidate("item-0000")
        val bottom = getCandidate("item-0006")
        val unavailableFootwear = getCandidate("item-0014").let {
            it.copy(attributes = it.attributes.copy(available = false))
        }

        val assignments = listOf(
            Assignment(FashionSlots.TOP, top),
            Assignment(FashionSlots.BOTTOM, bottom),
            Assignment(FashionSlots.FOOTWEAR, unavailableFootwear)
        )
        val rawId = SolutionCanonicalizer.canonicalize(assignments)
        val solution = FeasibleSolution(rawId, assignments, emptyList(), 10.0)

        val violations = EvidenceVerifier.verifySolutions(
            solutions = listOf(solution),
            knownCandidates = candidates,
            requirements = slots,
            hardConstraints = hardConstraints,
            context = context
        )

        assertThat(violations).isEqualTo(1)
    }

    @Test
    fun `unknownIdsAccepted correctly counts unexpected solution IDs`() {
        val solutions = listOf(
            FeasibleSolution<FashionCandidate>(SolutionId("sol-1"), emptyList(), emptyList(), 10.0),
            FeasibleSolution<FashionCandidate>(SolutionId("sol-2"), emptyList(), emptyList(), 9.0)
        )

        // Ranker returns ranked IDs, one is unknown
        val rankedIds = listOf(SolutionId("sol-1"), SolutionId("sol-unknown"))
        val acceptedCount = EvidenceVerifier.unknownIdsAccepted(rankedIds, solutions)
        assertThat(acceptedCount).isEqualTo(1)
    }
}
