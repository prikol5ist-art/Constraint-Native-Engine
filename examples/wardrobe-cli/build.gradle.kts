plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("io.github.constraintnative.examples.wardrobe.MainKt")
}

dependencies {
    implementation(project(":adapter-gemini"))
    implementation(project(":domain-fashion"))
    implementation(libs.kotlinx.serialization.json)
    
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}
