package io.github.constraintnative.fashion

import io.github.constraintnative.core.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FashionContextRendererTest {

    @Test
    fun `should render context correctly`() {
        val renderer = FashionContextRenderer()
        val context = FashionContext(18.5, Precipitation.RAIN, Occasion.SPORTY)
        val rendered = renderer.renderContext(context)
        assertThat(rendered).isEqualTo("Context: temp=18.5°C, precipitation=RAIN, occasion=SPORTY")
    }

    @Test
    fun `should render solution correctly`() {
        val renderer = FashionContextRenderer()
        val top = Candidate(
            id = CandidateId("topRed"),
            attributes = FashionCandidate("topRed", "Red Shirt", "Shirt", "red", 1, 0.4),
            eligibleSlots = setOf(FashionSlots.TOP)
        )
        val solution = FeasibleSolution(
            id = SolutionId("sol1"),
            assignments = listOf(Assignment(FashionSlots.TOP, top)),
            totalScore = 15.0,
            scoreComponents = listOf(ScoreComponent("color_harmony", 15.0))
        )
        val rendered = renderer.renderSolution(solution)
        assertThat(rendered).isEqualTo("top: Red Shirt (Shirt, color=red, layer=1, formalness=0.4)")
    }
}
