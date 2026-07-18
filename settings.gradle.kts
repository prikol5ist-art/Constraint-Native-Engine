pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "constraint-native-engine"

include(
    ":engine-core",
    ":engine-solver",
    ":engine-generative",
    ":adapter-gemini",
    ":domain-fashion",
    ":benchmark",
    ":examples:wardrobe-cli"
)
