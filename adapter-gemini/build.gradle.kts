plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("publishing-convention")
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    api(project(":engine-generative"))
    implementation(libs.kotlinx.serialization.json)
    
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

kover {
    reports {
        verify {
            rule("line coverage") {
                minBound(
                    85,
                    kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE,
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                )
            }
        }
    }
}
