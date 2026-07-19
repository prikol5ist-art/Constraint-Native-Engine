package io.github.constraintnative.benchmark.evidence

import java.io.File
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    if (args.size < 3) {
        System.err.println("Usage: EvidenceMain <generate|verify> <rootDir> <processorDescription>")
        exitProcess(1)
    }

    val mode = args[0]
    val rootDir = File(args[1])
    val processorDescription = args[2]

    if (mode != "generate" && mode != "verify") {
        System.err.println("Usage: EvidenceMain <generate|verify> <rootDir> <processorDescription>")
        exitProcess(1)
    }

    if (!rootDir.exists() || !rootDir.isDirectory) {
        System.err.println("Error: Root directory ${rootDir.absolutePath} does not exist or is not a directory.")
        exitProcess(1)
    }

    try {
        if (mode == "generate") {
            println("Running payload evidence benchmark...")
            val payloadReport = EvidenceBenchmark.runPayloadEvidence()
            
            // 2. Generate JSON
            val jsonText = EvidenceArtifacts.renderJson(payloadReport)
            val jsonFile = File(rootDir, "benchmark/reports/payload-evidence.json")
            jsonFile.parentFile.mkdirs()
            jsonFile.writeText(jsonText)
            println("Generated JSON report at: ${jsonFile.absolutePath}")

            // 3. Generate Markdown
            val mdText = EvidenceArtifacts.renderMarkdown(payloadReport)
            val mdFile = File(rootDir, "benchmark/reports/payload-evidence.md")
            mdFile.parentFile.mkdirs()
            mdFile.writeText(mdText)
            println("Generated Markdown report at: ${mdFile.absolutePath}")

            // 4. Generate SVG
            val svgText = EvidenceArtifacts.renderSvg(
                payloadReport.headline.payloadReductionPercent,
                payloadReport.headline.baselineBytes,
                payloadReport.headline.constrainedBytes
            )
            val svgFile = File(rootDir, "docs/assets/payload-reduction.svg")
            svgFile.parentFile.mkdirs()
            svgFile.writeText(svgText)
            println("Generated SVG at: ${svgFile.absolutePath}")

            // 5. Update README.md
            val readmeFile = File(rootDir, "README.md")
            if (!readmeFile.exists()) {
                throw IllegalStateException("README.md not found at ${readmeFile.absolutePath}")
            }
            val updatedReadme = EvidenceArtifacts.updateReadme(readmeFile, payloadReport.headline.payloadReductionPercent)
            readmeFile.writeText(updatedReadme)
            println("Updated README.md with payload reduction claim.")

            // 6. Run timing evidence
            println("Running timing evidence benchmark...")
            val timingReport = EvidenceBenchmark.runTimingEvidence(processorDescription = processorDescription)
            val timingJson = Json {
                prettyPrint = true
                prettyPrintIndent = "  "
                encodeDefaults = true
            }
            val timingText = timingJson.encodeToString(timingReport) + "\n"
            val timingFile = File(rootDir, "benchmark/reports/reference-timing-macos-arm64.json")
            timingFile.parentFile.mkdirs()
            timingFile.writeText(timingText)
            println("Generated reference timing report at: ${timingFile.absolutePath}")

            println("Evidence generation completed successfully.")
            exitProcess(0)
        } else {
            println("Verifying generated evidence artifacts against regenerated ones...")
            val currentPayloadReport = EvidenceBenchmark.runPayloadEvidence()
            val errors = EvidenceArtifacts.verify(rootDir, currentPayloadReport)
            if (errors.isNotEmpty()) {
                System.err.println("Verification FAILED:")
                for (error in errors) {
                    System.err.println("  - $error")
                }
                exitProcess(1)
            } else {
                println("Verification PASSED: Tracked artifacts and claim match regenerated output.")
                exitProcess(0)
            }
        }
    } catch (e: Exception) {
        System.err.println("Error occurred during $mode: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}
