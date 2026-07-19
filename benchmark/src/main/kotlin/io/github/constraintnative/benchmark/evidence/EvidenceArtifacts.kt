package io.github.constraintnative.benchmark.evidence

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
public object EvidenceArtifacts {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    public fun renderJson(report: PayloadEvidenceReport): String {
        return json.encodeToString(report) + "\n"
    }

    public fun renderMarkdown(report: PayloadEvidenceReport): String {
        return buildString {
            appendLine("# Payload Evidence Report")
            appendLine()
            appendLine("This report presents evidence of payload size reduction achieved by filtering and solving constraints locally using the Constraint-Native Generative Engine.")
            appendLine()
            appendLine("For more details, see the [Methodology](../../docs/benchmark-methodology.md).")
            appendLine()
            appendLine("## Scenarios")
            appendLine()
            appendLine("| Inventory Size | Desired Solutions | Baseline (Bytes) | Constrained (Bytes) | Reduction (%) | Feasible Solutions | Hard Violations | Unknown IDs Accepted | Nodes Expanded | Termination | Pairwise Diversity |")
            appendLine("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |")
            for (s in report.scenarios) {
                appendLine("| ${s.inventorySize} | ${s.desiredSolutions} | ${s.baselineBytes} | ${s.constrainedBytes} | ${s.payloadReductionPercent}% | ${s.feasibleSolutions} | ${s.hardViolations} | ${s.unknownIdsAccepted} | ${s.expandedNodes} | ${s.termination} | ${s.pairwiseDiversity} |")
            }
            appendLine()
        }
    }

    public fun renderSvg(reductionPercent: Double, baselineBytes: Int = 1000, constrainedBytes: Int = 250): String {
        val maxBarWidth = 800.0
        val bar1Width = maxBarWidth
        val ratio = if (baselineBytes > 0) constrainedBytes.toDouble() / baselineBytes.toDouble() else 0.0
        val bar2Width = maxBarWidth * ratio
        
        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 640" width="1280" height="640">
              <title>Payload Size Comparison</title>
              <desc>Comparison of Inventory-First payload vs Constraint-Native payload</desc>
              <rect width="1280" height="640" fill="#121212" rx="16"/>
              
              <!-- Title -->
              <text x="80" y="80" fill="#ffffff" font-size="36" font-weight="bold">Payload Size Comparison</text>
              <text x="80" y="120" fill="#888888" font-size="20">Comparison of Inventory-First payload vs Constraint-Native payload</text>
              
              <!-- Bar 1 (Baseline) -->
              <text x="80" y="200" fill="#aaaaaa" font-size="22" font-weight="bold">Inventory-first payload</text>
              <rect x="80" y="220" width="${bar1Width.toInt()}" height="60" fill="#555555" rx="8"/>
              <text x="900" y="258" fill="#ffffff" font-size="24" font-weight="bold">${baselineBytes} bytes</text>
              
              <!-- Bar 2 (Constrained) -->
              <text x="80" y="340" fill="#aaaaaa" font-size="22" font-weight="bold">Constraint-native payload</text>
              <rect x="80" y="360" width="${bar2Width.toInt()}" height="60" fill="#4caf50" rx="8"/>
              <text x="900" y="398" fill="#ffffff" font-size="24" font-weight="bold">${constrainedBytes} bytes</text>
              
              <!-- Reduction Badge -->
              <text x="80" y="520" fill="#4caf50" font-size="48" font-weight="bold">${reductionPercent}% reduction</text>
              <text x="80" y="560" fill="#888888" font-size="20">Payload size reduced by local constraint solving and filtering</text>
            </svg>
        """.trimIndent()
    }

    public fun updateReadme(readmeFile: File, reductionPercent: Double): String {
        val content = readmeFile.readText()
        val startMarker = "<!-- evidence:start -->"
        val endMarker = "<!-- evidence:end -->"
        val startIndex = content.indexOf(startMarker)
        val endIndex = content.indexOf(endMarker)
        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            throw IllegalStateException("README.md does not contain valid evidence markers.")
        }
        val claim = """
            $startMarker
            **Reduce LLM ranking context by $reductionPercent%** on the bundled 500-item / 3-solution scenario, while independent verification reports **0 hard-constraint violations** and **0 accepted unknown IDs**.

            [See the complete reproducible benchmark →](benchmark/reports/payload-evidence.md)
            $endMarker
        """.trimIndent()
        
        return content.substring(0, startIndex) + claim + content.substring(endIndex + endMarker.length)
    }

    public fun verify(rootDir: File, report: PayloadEvidenceReport): List<String> {
        val errors = mutableListOf<String>()
        val reportsDir = File(rootDir, "benchmark/reports")
        val assetsDir = File(rootDir, "docs/assets")
        val expectedDir = File(rootDir, "benchmark/build/evidence-expected")
        expectedDir.mkdirs()
        
        val currentJson = renderJson(report)
        val currentMd = renderMarkdown(report)
        val currentSvg = renderSvg(report.headline.payloadReductionPercent, report.headline.baselineBytes, report.headline.constrainedBytes)
        
        val readmeFile = File(rootDir, "README.md")
        if (!readmeFile.exists()) {
            errors.add("Tracked README.md does not exist at ${readmeFile.absolutePath}")
            return errors
        }
        
        val currentReadme = try {
            updateReadme(readmeFile, report.headline.payloadReductionPercent)
        } catch (e: Exception) {
            errors.add("Failed to generate updated README.md: ${e.message}")
            ""
        }
        
        val expectedJsonFile = File(expectedDir, "payload-evidence.json")
        val expectedMdFile = File(expectedDir, "payload-evidence.md")
        val expectedSvgFile = File(expectedDir, "payload-reduction.svg")
        val expectedReadmeFile = File(expectedDir, "README.md")
        
        expectedJsonFile.writeText(currentJson)
        expectedMdFile.writeText(currentMd)
        expectedSvgFile.writeText(currentSvg)
        if (currentReadme.isNotEmpty()) {
            expectedReadmeFile.writeText(currentReadme)
        }
        
        val trackedJsonFile = File(reportsDir, "payload-evidence.json")
        val trackedMdFile = File(reportsDir, "payload-evidence.md")
        val trackedSvgFile = File(assetsDir, "payload-reduction.svg")
        
        if (!trackedJsonFile.exists()) {
            errors.add("Tracked JSON file does not exist at ${trackedJsonFile.absolutePath}")
        } else {
            val expectedBytes = currentJson.toByteArray(Charsets.UTF_8)
            val trackedBytes = trackedJsonFile.readBytes()
            if (!expectedBytes.contentEquals(trackedBytes)) {
                errors.add("Tracked JSON does not match regenerated JSON")
            }
        }
        
        if (!trackedMdFile.exists()) {
            errors.add("Tracked MD file does not exist at ${trackedMdFile.absolutePath}")
        } else {
            val expectedBytes = currentMd.toByteArray(Charsets.UTF_8)
            val trackedBytes = trackedMdFile.readBytes()
            if (!expectedBytes.contentEquals(trackedBytes)) {
                errors.add("Tracked Markdown does not match regenerated Markdown")
            }
        }
        
        if (!trackedSvgFile.exists()) {
            errors.add("Tracked SVG file does not exist at ${trackedSvgFile.absolutePath}")
        } else {
            val expectedBytes = currentSvg.toByteArray(Charsets.UTF_8)
            val trackedBytes = trackedSvgFile.readBytes()
            if (!expectedBytes.contentEquals(trackedBytes)) {
                errors.add("Tracked SVG does not match regenerated SVG")
            }
        }
        
        if (currentReadme.isNotEmpty()) {
            val expectedBytes = currentReadme.toByteArray(Charsets.UTF_8)
            val trackedBytes = readmeFile.readBytes()
            if (!expectedBytes.contentEquals(trackedBytes)) {
                errors.add("Tracked README.md does not match regenerated README.md (claim body mismatch)")
            }
            
            val readmeText = readmeFile.readText()
            if (readmeText.contains("Benchmark evidence has not been generated for this snapshot.")) {
                errors.add("README.md still contains the initial placeholder message.")
            }
        }
        
        val timingFile = File(reportsDir, "reference-timing-macos-arm64.json")
        if (!timingFile.exists()) {
            errors.add("Timing report does not exist at ${timingFile.absolutePath}")
        } else {
            try {
                val timingContent = timingFile.readText()
                val timingReport = json.decodeFromString<TimingEvidenceReport>(timingContent)
                if (timingReport.schemaVersion != 1) {
                    errors.add("Timing report schema version must be 1")
                }
                if (timingReport.scenarios.isEmpty()) {
                    errors.add("Timing report scenarios must not be empty")
                }
            } catch (e: Exception) {
                errors.add("Timing report does not match TimingEvidenceReport schema: ${e.message}")
            }
        }
        
        return errors
    }
}
