plugins {
    alias(libs.plugins.kotlin.jvm)
    id("publishing-convention")
    alias(libs.plugins.kover)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    api(project(":engine-solver"))
    implementation(libs.kotlinx.coroutines.core)
    
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
                    90,
                    kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE,
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                )
            }
        }
    }
}
