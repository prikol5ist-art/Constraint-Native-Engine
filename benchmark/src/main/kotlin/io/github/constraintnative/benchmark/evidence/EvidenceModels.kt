package io.github.constraintnative.benchmark.evidence

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.round

@Serializable
data class PayloadEvidenceReport(
    val schemaVersion: Int,
    val headline: PayloadScenarioReport,
    val scenarios: List<PayloadScenarioReport>
)

@Serializable
data class PayloadScenarioReport(
    val inventorySize: Int,
    val desiredSolutions: Int,
    val seed: Long,
    val baselineBytes: Int,
    val constrainedBytes: Int,
    val baselineEstimatedTokens: Int,
    val constrainedEstimatedTokens: Int,
    val payloadReductionPercent: Double,
    val feasibleSolutions: Int,
    val hardViolations: Int,
    val unknownIdsAccepted: Int,
    val expandedNodes: Long,
    val termination: String,
    val pairwiseDiversity: Double
)

@Serializable
data class TimingEvidenceReport(
    val schemaVersion: Int,
    val generatedOn: String,
    val osName: String,
    val osArchitecture: String,
    val jvm: String,
    val processorDescription: String,
    val warmups: Int,
    val samples: Int,
    val scenarios: List<TimingScenarioReport>
)

@Serializable
data class TimingScenarioReport(
    val inventorySize: Int,
    val p50Millis: Double,
    val p95Millis: Double
)

fun estimatedTokens(utf8Bytes: Int): Int = ceil(utf8Bytes / 4.0).toInt()

fun payloadReductionPercent(baselineBytes: Int, constrainedBytes: Int): Double {
    require(baselineBytes > 0) { "baselineBytes must be positive" }
    require(constrainedBytes in 0..baselineBytes) { "constrainedBytes must be within baseline payload" }
    return round((baselineBytes - constrainedBytes) * 10_000.0 / baselineBytes) / 100.0
}
