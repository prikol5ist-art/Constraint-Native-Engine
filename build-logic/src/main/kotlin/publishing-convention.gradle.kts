import org.gradle.api.tasks.bundling.Jar

plugins {
    `maven-publish`
    `java`
}

java {
    withSourcesJar()
}

val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.named("dokkaJavadoc"))
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("dokka/javadoc"))
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(dokkaJavadocJar)
            pom {
                name.set(project.name)
                description.set("Constraint Native Engine - high-performance type-safe combinatorial optimization")
                url.set("https://github.com/constraint-native/constraint-native-engine")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("developer")
                        name.set("Constraint Native Developers")
                        email.set("dev@constraintnative.org")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/constraint-native/constraint-native-engine.git")
                    developerConnection.set("scm:git:ssh://github.com/constraint-native/constraint-native-engine.git")
                    url.set("https://github.com/constraint-native/constraint-native-engine")
                }
            }
        }
    }
}
