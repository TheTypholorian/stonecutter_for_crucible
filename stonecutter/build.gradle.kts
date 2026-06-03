@file:Suppress("UnstableApiUsage")
@file:OptIn(ExperimentalPathApi::class)

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.net.URI
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
    alias(common.plugins.kotlin.dokka)
    alias(common.plugins.kotlin.dokka.javadoc)
    id("shared-setup")
}

version = property("version") as String
val baseVersion: String = property("version").toString()
    .substringBefore('-').substringBeforeLast('.')

sourceSets.main {
    kotlin.srcDirs(files("build/generated/ver/main/kotlin").builtBy("generateVersion"), files("src/samples/kotlin"))
}

dependencies {
    api(project(":stitcher"))
    api(common.misc.semver)
    api(common.misc.commons)
    implementation(common.kotlin.serialization)
    implementation(common.kotlin.serialization.json)
    implementation(common.kotlin.serialization.toml)
    implementation(common.kotlin.serialization.yaml)

    testImplementation(gradleTestKit())
    testImplementation(common.kotest.runner)
    testImplementation(common.kotest.assertions)
    testImplementation(common.misc.mordant)

    dokkaHtmlPlugin(common.kotlin.dokka.versioning)
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

dokka {
    moduleName = "Stonecutter Gradle"

    dokkaPublications.html {
        suppressInheritedMembers = true
        suppressObviousFunctions = true
    }

    pluginsConfiguration.html {
        homepageLink = "https://stonecutter.kikugie.dev/"
        footerMessage = "(c) 2026 KikuGie"
    }

    dokkaSourceSets.named("main") {
        reportUndocumented = true
        skipEmptyPackages = true

        sourceLink {
            localDirectory = file("src/main/kotlin")
            remoteLineSuffix = "#L"
            remoteUrl("https://codeberg.org/stonecutter/stonecutter/src/branch/$baseVersion/stonecutter/")
        }

        externalDocumentationLinks.register("kotlin-stdlib") {
            url("https://kotlinlang.org/api/core/")
        }

        externalDocumentationLinks.register("kotlinx-serialization") {
            url("https://kotlinlang.org/api/kotlinx.serialization/")
        }

        externalDocumentationLinks.register("gradle-kotlin-dsl") {
            url("https://docs.gradle.org/current/kotlin-dsl/")
            packageListUrl("https://docs.gradle.org/current/kotlin-dsl/gradle/package-list")
        }

        externalDocumentationLinks.register("semver") {
            val version = common.versions.misc.semver.get()
            url("https://javadoc.io/static/dev.kikugie/semver/$version")
            packageListUrl.set(URI.create("https://javadoc.io/static/dev.kikugie/semver/$version/semver/package-list"))
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

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
        dependencies {
            exclude(dependency("com.squareup.okio:.*"))
            exclude(dependency("net.thauvin.erik:.*"))
            exclude(dependency("com.ibm.icu:.*"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib:.*"))
        }

        exclude("idea_config.xml")
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
