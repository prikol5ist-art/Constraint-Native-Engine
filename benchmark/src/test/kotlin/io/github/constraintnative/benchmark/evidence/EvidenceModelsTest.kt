package io.github.constraintnative.benchmark.evidence

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
class EvidenceModelsTest {
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    @Test
    fun `report JSON is stable and contains schema version`() {
        val scenario = PayloadScenarioReport(
            inventorySize = 500,
            desiredSolutions = 3,
            seed = 20260719L,
            baselineBytes = 10_000,
            constrainedBytes = 2_500,
            baselineEstimatedTokens = 2_500,
            constrainedEstimatedTokens = 625,
            payloadReductionPercent = 75.0,
            feasibleSolutions = 3,
            hardViolations = 0,
            unknownIdsAccepted = 0,
            expandedNodes = 321,
            termination = "BOUNDED_SEARCH_COMPLETED",
            pairwiseDiversity = 0.75
        )
        val report = PayloadEvidenceReport(schemaVersion = 1, headline = scenario, scenarios = listOf(scenario))

        assertThat(json.encodeToString(report)).contains(
            "\"schemaVersion\": 1",
            "\"inventorySize\": 500",
            "\"payloadReductionPercent\": 75.0"
        )
    }

    @Test
    fun `reduction uses UTF-8 byte counts`() {
        assertThat(payloadReductionPercent(10_000, 2_500)).isEqualTo(75.0)
        assertThat(estimatedTokens(10_000)).isEqualTo(2_500)
    }
}
