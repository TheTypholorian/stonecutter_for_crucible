@file:Suppress("UnstableApiUsage")
@file:OptIn(ExperimentalPathApi::class)

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import kotlin.io.path.ExperimentalPathApi

plugins {
    java
    signing
    `kotlin-dsl`
    `maven-publish`
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(common.plugins.gradle.shadow)
    alias(common.plugins.gradle.publish)
    alias(common.plugins.kotlin.validator)
    id("shared-setup")
}

version = property("version") as String

sourceSets.main {
    kotlin.srcDir(files("build/generated/ver/main/kotlin").builtBy("generateVersion"))
}

dependencies {
    api(project(":stitcher"))
    api(common.misc.semver)
    api(common.misc.commons)
    implementation(common.kotlin.stdlib)
    implementation(common.kotlin.reflect)
    implementation(common.kotlin.serialization)
    implementation(common.kotlin.serialization.json)
    implementation(common.kotlin.serialization.toml)

    testImplementation(gradleTestKit())
    testImplementation(common.kotest.runner)
    testImplementation(common.kotest.assertions)

    testImplementation(common.ktor.json)
    testImplementation(common.ktor.client)
    testImplementation(common.ktor.client.java)
    testImplementation(common.ktor.client.negotiation)

    testImplementation(common.misc.mordant)
}

apiValidation {
    apiDumpDirectory = "src/api"
}

java {
    withSourcesJar()
}

kotlin {
    explicitApi()
}

tasks {
    compileKotlin {
        mustRunAfter("generateVersion")
    }

    compileTestKotlin {
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }

    publishPlugins {
        dependOnPublishTasks()
    }

    publish {
        finalizedBy(publishToMavenLocal)
    }

    shadowJar {
        archiveClassifier = ""
        minimize()
        exclude("kotlin/**", "com/ibm/**", "org/jetbrains/**", "**/*.knm", "**/*.pro", "commonMain/**", "nativeMain/**")
    }

    register<GenerateVersionTask>("generateVersion") {
        version = project.version as String
        file = file("build/generated/ver/main/kotlin/dev/kikugie/stonecutter/__version.kt")
    }

    register<ShadowJar>("slimJar") {
        group = "build"
        archiveClassifier = "slim"
        configurations = project.configurations.runtimeClasspath.map(::listOf)

        from(sourceSets.main.map(SourceSet::getOutput))
        dependencies {
            include(project(":stitcher"))
        }
    }
}

gradlePlugin {
    website = "https://stonecutter.kikugie.dev/"
    vcsUrl = "https://codeberg.org/stonecutter/stonecutter"

    plugins {
        create("stonecutter") {
            id = "dev.kikugie.stonecutter"
            implementationClass = "dev.kikugie.stonecutter.StonecutterPlugin"
            displayName = "Stonecutter"
            description = "Modern Gradle plugin for multi-version management"
            tags.addAll("preprocessor", "java", "kotlin", "resources")
        }
    }
}

publishing {
    repositories {
        fun register(type: String, build: MavenArtifactRepository.() -> Unit) {
            val username = findProperty("mvn.$type.username") as String?
            val password = findProperty("mvn.$type.password") as String?
            if (username == null || password == null)
                return println("Missing credentials for $type maven repository")

            maven {
                build()
                credentials {
                    this.username = username
                    this.password = password
                }
            }
        }

        register("kikugie") {
            name = "KikuGieMaven"
            url = when {
                '-' in project.version.toString() -> uri("https://maven.kikugie.dev/snapshots")
                else -> uri("https://maven.kikugie.dev/releases")
            }
        }
    }

    publications {
        register<MavenPublication>("maven") {
            groupId = "dev.kikugie"
            artifactId = "stonecutter"
            version = project.version.toString()
            from(components["java"])
            artifact(tasks.named("slimJar"))

            pom {
                name = "Stonecutter"
                description = "Modern Gradle plugin for multi-version management"
                url = "https://stonecutter.kikugie.dev/"

                developers {
                    developer {
                        id = "kikugie"
                        name = "KikuGie"
                        email = "kikugie@duck.com"
                    }
                }

                licenses {
                    license {
                        name = "GNU Lesser Public License 3.0"
                        url = "https://www.gnu.org/licenses/lgpl-3.0.en.html"
                    }
                }

                scm {
                    connection = "scm:git:git:https://codeberg.org/stonecutter/stonecutter.git"
                    developerConnection = "scm:git:ssh://codeberg.org:stonecutter/stonecutter.git"
                    url = "https://codeberg.org/stonecutter/stonecutter"
                }
            }
        }
    }
}

signing {
    sign(configurations.runtimeElements.get())
}
