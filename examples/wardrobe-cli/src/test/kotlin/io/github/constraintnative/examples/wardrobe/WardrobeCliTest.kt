package io.github.constraintnative.examples.wardrobe

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class WardrobeCliTest {

    @Test
    fun `cli runs successfully with valid options and matches expected output`() {
        val javaHome = System.getProperty("java.home")
        val javaBin = javaHome + File.separator + "bin" + File.separator + "java"
        val classpath = System.getProperty("java.class.path")
        val className = "io.github.constraintnative.examples.wardrobe.MainKt"

        val process = ProcessBuilder(
            javaBin,
            "-cp",
            classpath,
            className,
            "--occasion=formal",
            "--temperature=8.0",
            "--diversityWeight=0.5"
        ).start()

        val exitCode = process.waitFor()
        val stdout = process.inputStream.bufferedReader().readText()

        assertThat(exitCode).isEqualTo(0)

        // Read golden expected output
        val expectedStream = javaClass.classLoader.getResourceAsStream("expected-formal-output.txt")
            ?: throw IllegalStateException("expected-formal-output.txt not found on classpath")
        val expectedText = expectedStream.bufferedReader().use { it.readText() }

        // Normalize newlines to \n and compare
        val normalizedStdout = stdout.replace("\r\n", "\n").trim()
        val normalizedExpected = expectedText.replace("\r\n", "\n").trim()

        assertThat(normalizedStdout).isEqualTo(normalizedExpected)
    }

    @Test
    fun `cli exits with status 2 on invalid options`() {
        val javaHome = System.getProperty("java.home")
        val javaBin = javaHome + File.separator + "bin" + File.separator + "java"
        val classpath = System.getProperty("java.class.path")
        val className = "io.github.constraintnative.examples.wardrobe.MainKt"

        val process = ProcessBuilder(
            javaBin,
            "-cp",
            classpath,
            className,
            "--temperature=NaN"
        ).start()

        val exitCode = process.waitFor()
        val stderr = process.errorStream.bufferedReader().readText()

        assertThat(exitCode).isEqualTo(2)
        assertThat(stderr).contains("--temperature must be finite")
    }

    @Test
    fun `README documents the verified offline command`() {
        val rootPath = requireNotNull(System.getProperty("repositoryRoot")) {
            "System property repositoryRoot is not set"
        }
        val root = File(rootPath)
        val readme = root.resolve("README.md").readText()
        assertThat(readme).contains(
            "./gradlew :examples:wardrobe-cli:run --args='--occasion formal --temperature 8 --diversityWeight 0.5'",
            "Ranker status: NOT_CONFIGURED",
            "Feasible Outfits Found:"
        )
    }
}
