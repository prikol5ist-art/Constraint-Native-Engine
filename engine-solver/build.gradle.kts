plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    explicitApi()
    jvmToolchain(17)
}

dependencies {
    api(project(":engine-core"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}
