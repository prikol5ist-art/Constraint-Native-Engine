package io.github.constraintnative.benchmark.evidence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class EvidenceArtifactsTest {

    @Test
    fun `evidence markdown contains all scenarios and no cost-billing claims`() {
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

        val md = EvidenceArtifacts.renderMarkdown(report)
        assertThat(md).contains("500", "75.0%", "0", "BOUNDED_SEARCH_COMPLETED")
        assertThat(md).doesNotContain("billing", "cost reduction", "same quality")
        assertThat(md).contains("benchmark-methodology.md")
    }

    @Test
    fun `SVG is well-formed plain SVG and includes title`() {
        val svg = EvidenceArtifacts.renderSvg(75.25)
        assertThat(svg).contains("<svg", "</svg>", "<title>", "75.25%")
        assertThat(svg).doesNotContain("<script", "url(", "font-family")
    }

    @Test
    fun `JSON ends with one newline and round-trips to the same report`() {
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

        val json = EvidenceArtifacts.renderJson(report)
        assertThat(json).endsWith("\n")
        assertThat(json.substring(0, json.length - 1)).doesNotEndWith("\n")

        val roundTrip = kotlinx.serialization.json.Json.decodeFromString<PayloadEvidenceReport>(json)
        assertThat(roundTrip).isEqualTo(report)
    }

    @Test
    fun `README replacement modifies only the marker body`() {
        val tempFile = File.createTempFile("README-test", ".md")
        tempFile.deleteOnExit()
        val originalContent = """
            # Project Title
            Some description.
            
            <!-- evidence:start -->
            Benchmark evidence has not been generated for this snapshot.
            <!-- evidence:end -->
            
            Other content.
        """.trimIndent()
        tempFile.writeText(originalContent)

        val updated = EvidenceArtifacts.updateReadme(tempFile, 74.5)
        
        assertThat(updated).contains("<!-- evidence:start -->")
        assertThat(updated).contains("<!-- evidence:end -->")
        assertThat(updated).contains("74.5%")
        assertThat(updated).contains("# Project Title")
        assertThat(updated).contains("Other content.")
        
        val partsBeforeOriginal = originalContent.split("<!-- evidence:start -->")[0]
        val partsAfterOriginal = originalContent.split("<!-- evidence:end -->")[1]
        
        val partsBeforeUpdated = updated.split("<!-- evidence:start -->")[0]
        val partsAfterUpdated = updated.split("<!-- evidence:end -->")[1]
        
        assertThat(partsBeforeUpdated).isEqualTo(partsBeforeOriginal)
        assertThat(partsAfterUpdated).isEqualTo(partsAfterOriginal)
    }

    @Test
    fun `verification fails when one byte in any tracked artifact changes`() {
        val tempRootDir = java.nio.file.Files.createTempDirectory("cne-root").toFile()
        tempRootDir.deleteOnExit()
        
        val reportsDir = File(tempRootDir, "benchmark/reports")
        reportsDir.mkdirs()
        val assetsDir = File(tempRootDir, "docs/assets")
        assetsDir.mkdirs()
        
        val readmeFile = File(tempRootDir, "README.md")
        val readmeTemplate = """
            # README
            <!-- evidence:start -->
            Benchmark evidence has not been generated for this snapshot.
            <!-- evidence:end -->
        """.trimIndent()
        readmeFile.writeText(readmeTemplate)
        
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
        
        val validJson = EvidenceArtifacts.renderJson(report)
        val validMd = EvidenceArtifacts.renderMarkdown(report)
        val validSvg = EvidenceArtifacts.renderSvg(75.0, 10000, 2500)
        val validReadme = EvidenceArtifacts.updateReadme(readmeFile, 75.0)
        
        val timingFile = File(reportsDir, "reference-timing-macos-arm64.json")
        val timingJson = """
            {
              "schemaVersion": 1,
              "generatedOn": "2026-07-19T16:24:58Z",
              "osName": "Mac OS X",
              "osArchitecture": "aarch64",
              "jvm": "17.0.1",
              "processorDescription": "Apple Silicon reference machine",
              "warmups": 5,
              "samples": 30,
              "scenarios": [
                {
                  "inventorySize": 25,
                  "p50Millis": 0.5,
                  "p95Millis": 1.2
                }
              ]
            }
        """.trimIndent()
        timingFile.writeText(timingJson)
        
        File(reportsDir, "payload-evidence.json").writeText(validJson)
        File(reportsDir, "payload-evidence.md").writeText(validMd)
        File(assetsDir, "payload-reduction.svg").writeText(validSvg)
        readmeFile.writeText(validReadme)
        
        val errorsBeforeChange = EvidenceArtifacts.verify(tempRootDir, report)
        assertThat(errorsBeforeChange).isEmpty()
        
        File(reportsDir, "payload-evidence.json").writeText(validJson + " ")
        val errorsJsonChange = EvidenceArtifacts.verify(tempRootDir, report)
        assertThat(errorsJsonChange).isNotEmpty()
        File(reportsDir, "payload-evidence.json").writeText(validJson)
        
        File(reportsDir, "payload-evidence.md").writeText(validMd.dropLast(1) + "X")
        val errorsMdChange = EvidenceArtifacts.verify(tempRootDir, report)
        assertThat(errorsMdChange).isNotEmpty()
        File(reportsDir, "payload-evidence.md").writeText(validMd)
        
        File(assetsDir, "payload-reduction.svg").writeText(validSvg + "\n")
        val errorsSvgChange = EvidenceArtifacts.verify(tempRootDir, report)
        assertThat(errorsSvgChange).isNotEmpty()
        File(assetsDir, "payload-reduction.svg").writeText(validSvg)
        
        readmeFile.writeText(validReadme.replace("0 hard-constraint", "1 hard-constraint"))
        val errorsReadmeChange = EvidenceArtifacts.verify(tempRootDir, report)
        assertThat(errorsReadmeChange).isNotEmpty()
        readmeFile.writeText(validReadme)
        
        timingFile.delete()
        val errorsTimingDelete = EvidenceArtifacts.verify(tempRootDir, report)
        assertThat(errorsTimingDelete).isNotEmpty()
    }

    @Test
    fun `README contains all required structure in order and rejects forbidden terms`() {
        val rootPath = System.getProperty("repositoryRoot") ?: "."
        var root = File(rootPath)
        if (!root.resolve("README.md").exists() && root.resolve("../README.md").exists()) {
            root = root.parentFile
        }
        val readmeFile = root.resolve("README.md")
        if (!readmeFile.exists()) {
            return
        }
        val readmeText = readmeFile.readText()

        // 1. Order check
        val idxEvidence = readmeText.indexOf("evidence:start")
        val idxQuickStart = readmeText.indexOf("Quick Start")
        val idxGuarantees = readmeText.indexOf("Guarantees")
        val idxArchitecture = readmeText.indexOf("Architecture Overview")
        val idxUsage = readmeText.indexOf("Code Examples")
        val idxGemini = readmeText.indexOf("generative ranking")
        val idxBenchmark = readmeText.indexOf("Building and Running Benchmarks")
        val idxAlpha = readmeText.indexOf("Maturity")
        val idxLinks = readmeText.indexOf("License")

        val indices = listOf(idxEvidence, idxQuickStart, idxGuarantees, idxArchitecture, idxUsage, idxGemini, idxBenchmark, idxAlpha, idxLinks)
        val names = listOf("evidence:start", "Quick Start", "Guarantees", "Architecture Overview", "Code Examples", "generative ranking", "Building and Running Benchmarks", "Maturity", "License")
        for (i in indices.indices) {
            assertThat(indices[i]).describedAs("Section '${names[i]}' not found").isNotNegative()
        }
        for (i in 0 until indices.size - 1) {
            assertThat(indices[i]).describedAs("Section '${names[i]}' is not before '${names[i+1]}'").isLessThan(indices[i+1])
        }

        // 2. Reject forbidden terms
        assertThat(readmeText).doesNotContain("high-performance")
        assertThat(readmeText).doesNotContain("multiplatform")
        assertThat(readmeText).doesNotContain("same quality")
        assertThat(readmeText).doesNotContain("global optimum")
        assertThat(readmeText).doesNotContain("token cost reduction")
    }
}
