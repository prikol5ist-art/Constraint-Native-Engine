plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    alias(libs.plugins.binary.compatibility)
    alias(libs.plugins.benchmark) apply false
}

group = "io.github.constraintnative"
version = "0.1.0-SNAPSHOT"

allprojects {
    group = rootProject.group
    version = rootProject.version
}

apiValidation {
    ignoredProjects.addAll(listOf("benchmark", "wardrobe-cli"))
}

subprojects {
    plugins.apply("org.jetbrains.dokka")
    tasks.withType<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>().configureEach {
        enabled = false
    }
    val projectPath = path
    if (projectPath.contains("benchmark") || projectPath.contains("examples") || projectPath.contains("wardrobe-cli")) {
        tasks.withType<org.jetbrains.dokka.gradle.DokkaTaskPartial>().configureEach {
            enabled = false
        }
    }
}

tasks.named<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>("dokkaHtmlMultiModule") {
    outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
}
