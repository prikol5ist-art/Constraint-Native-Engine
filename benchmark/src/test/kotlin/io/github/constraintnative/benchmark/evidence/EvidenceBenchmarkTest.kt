package io.github.constraintnative.benchmark.evidence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EvidenceBenchmarkTest {
    @Test
    fun `headline scenario is 500 items and three bounded solutions`() {
        val report = EvidenceBenchmark.runPayloadEvidence()
        assertThat(report.schemaVersion).isEqualTo(1)
        assertThat(report.headline.inventorySize).isEqualTo(500)
        assertThat(report.headline.desiredSolutions).isEqualTo(3)
        assertThat(report.headline.hardViolations).isZero()
        assertThat(report.headline.unknownIdsAccepted).isZero()
        assertThat(report.headline.baselineBytes).isGreaterThan(report.headline.constrainedBytes)
        assertThat(report.scenarios.map { it.inventorySize }).containsExactly(25, 100, 500, 1_000)
    }

    @Test
    fun `runPayloadEvidence is completely deterministic and equal across runs`() {
        val report1 = EvidenceBenchmark.runPayloadEvidence()
        val report2 = EvidenceBenchmark.runPayloadEvidence()
        assertThat(report1).isEqualTo(report2)
    }

    @Test
    fun `runTimingEvidence executes and returns correct structure`() {
        val report = EvidenceBenchmark.runTimingEvidence(warmups = 1, samples = 2, processorDescription = "Test CPU")
        assertThat(report.schemaVersion).isEqualTo(1)
        assertThat(report.processorDescription).isEqualTo("Test CPU")
        assertThat(report.warmups).isEqualTo(1)
        assertThat(report.samples).isEqualTo(2)
        assertThat(report.scenarios).hasSize(4)
        for (scenario in report.scenarios) {
            assertThat(scenario.p50Millis).isGreaterThanOrEqualTo(0.0)
            assertThat(scenario.p95Millis).isGreaterThanOrEqualTo(scenario.p50Millis)
        }
    }
}
