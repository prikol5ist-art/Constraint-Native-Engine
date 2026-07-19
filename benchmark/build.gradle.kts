plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.benchmark)
}

kotlin {
    jvmToolchain(17)
}

benchmark {
    configurations {
        named("main") {
            iterations = 1
            warmups = 0
            iterationTime = 1
            iterationTimeUnit = "s"
        }
    }
    targets {
        register("main")
    }
}

dependencies {
    implementation(project(":domain-fashion"))
    implementation(project(":adapter-gemini"))
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:${libs.versions.benchmark.get()}")

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("repositoryRoot", rootProject.projectDir.absolutePath)
}

val evidenceClasspath = sourceSets.main.get().runtimeClasspath

fun JavaExec.configureEvidence(mode: String) {
    group = "verification"
    classpath = evidenceClasspath
    mainClass.set("io.github.constraintnative.benchmark.evidence.EvidenceMainKt")
    args(
        mode,
        rootProject.projectDir.absolutePath,
        "Apple Silicon reference machine"
    )
}

tasks.register<JavaExec>("generateEvidence") {
    description = "Generate deterministic payload evidence and reference timing artifacts"
    configureEvidence("generate")
}

tasks.register<JavaExec>("verifyEvidence") {
    description = "Verify tracked evidence and README claim match regenerated output"
    configureEvidence("verify")
}

