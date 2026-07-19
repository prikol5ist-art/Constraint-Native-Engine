package io.github.constraintnative.benchmark.evidence

import io.github.constraintnative.core.Assignment
import io.github.constraintnative.core.FeasibleSolution
import io.github.constraintnative.core.ScoreComponent
import io.github.constraintnative.core.SolutionId
import io.github.constraintnative.fashion.FashionContext
import io.github.constraintnative.fashion.Occasion
import io.github.constraintnative.fashion.Precipitation
import io.github.constraintnative.fashion.FashionSlots
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class EvidencePayloadRendererTest {

    private fun defaultContext() = FashionContext(
        temperatureCelsius = 12.0,
        precipitation = Precipitation.RAIN,
        occasion = Occasion.CASUAL
    )

    @Test
    fun `inventory payload is stable and contains ranking fields only`() {
        val candidates = SyntheticFashionInventory.create(25, 20260719L).shuffled(Random(7))
        val payload = EvidencePayloadRenderer.inventoryPayload(candidates, defaultContext())
        assertThat(payload).startsWith("context=")
        assertThat(payload.indexOf("item-0000")).isLessThan(payload.indexOf("item-0001"))
        assertThat(payload).doesNotContain("imagePath", "notes", "/Users/")
        assertThat(EvidencePayloadRenderer.utf8Bytes(payload)).isEqualTo(payload.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `solution payload is stable and sorted by ID`() {
        val candidates = SyntheticFashionInventory.create(20, 20260719L)
        val candidateMap = candidates.associateBy { it.id }

        // Construct two FeasibleSolution instances in reverse order of SolutionId
        val sol2 = FeasibleSolution(
            id = SolutionId("sol-b"),
            assignments = listOf(
                Assignment(FashionSlots.FOOTWEAR, candidateMap.values.first { it.id.value == "item-0014" }),
                Assignment(FashionSlots.TOP, candidateMap.values.first { it.id.value == "item-0000" })
            ),
            scoreComponents = listOf(ScoreComponent("test", 10.0)),
            totalScore = 10.0
        )

        val sol1 = FeasibleSolution(
            id = SolutionId("sol-a"),
            assignments = listOf(
                Assignment(FashionSlots.TOP, candidateMap.values.first { it.id.value == "item-0000" }),
                Assignment(FashionSlots.FOOTWEAR, candidateMap.values.first { it.id.value == "item-0014" })
            ),
            scoreComponents = listOf(ScoreComponent("test", 20.0)),
            totalScore = 20.0
        )

        // Render solutions
        val payload = EvidencePayloadRenderer.solutionsPayload(listOf(sol2, sol1))
        assertThat(payload).contains("sol-a")
        assertThat(payload).contains("sol-b")
        assertThat(payload.indexOf("sol-a")).isLessThan(payload.indexOf("sol-b"))
        // Check sorted assignments in a line: foot -> top (sorted by slot name alphabetically: footwear then top)
        // Ensure "footwear" slot output is rendered before "top" because footwear starts with 'f' and top starts with 't'
        assertThat(payload).contains("assignments=footwear:item-0014,top:item-0000")
    }
}
