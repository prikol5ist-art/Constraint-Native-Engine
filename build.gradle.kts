plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka) apply false
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
