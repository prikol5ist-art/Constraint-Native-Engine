plugins {
    alias(libs.plugins.kotlin.jvm)
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
    implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:${libs.versions.benchmark.get()}")
    
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}
