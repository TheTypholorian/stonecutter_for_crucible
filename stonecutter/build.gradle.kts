@file:Suppress("UnstableApiUsage")

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.plugin.compatibility.compatibility
import java.net.URI
import kotlin.io.path.ExperimentalPathApi

plugins {
    java
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.assignment") version common.versions.kotlin.jvm.get()
    alias(common.plugins.gradle.shadow)
    alias(common.plugins.gradle.publish)
    alias(common.plugins.kotlin.dokka)
    alias(common.plugins.kotlin.dokka.javadoc)
    alias(common.plugins.kotlin.samreceiver)
    alias(common.plugins.kotlin.testballoon)
    id("shared-setup")
}

version = property("version") as String
val baseVersion: String = property("version").toString()
    .substringBefore('-')
    .let {
        val trimmed = it.substringBeforeLast('.')
        if ('.' in trimmed) trimmed else it
    }

sourceSets.main {
    kotlin.srcDirs(files("build/generated/ver/main/kotlin").builtBy("generateVersion"), files("src/samples/kotlin"))
}

dependencies {
    api(common.misc.semver)
    api(common.misc.commons)
    api(project(":annotations"))
    api(project(":model"))
    api(project(":stitcher"))

    compileOnly(gradleKotlinDsl())
    implementation(common.kotlin.serialization)
    implementation(common.kotlin.serialization.json)
    implementation(common.kotlin.serialization.toml)
    implementation(common.kotlin.serialization.yaml)

    testImplementation(gradleTestKit())
    testImplementation(common.kotlin.testballoon)
    testImplementation(common.kotlin.testballoon.assertions)

    dokkaHtmlPlugin(common.kotlin.dokka.versioning)
}

java {
    withSourcesJar()
}

kotlin {
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                annotatedWith.add("dev.kikugie.stonecutter.StonecutterInternalAPI")
            }
        }
    }
}

samWithReceiver {
    annotation(HasImplicitReceiver::class.qualifiedName!!)
}

assignment {
    annotation("org.gradle.api.SupportsKotlinAssignmentOverloading")
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

        customAssets.from(isolated.rootProject.projectDirectory.dir("tools/dokka-icons").asFileTree.matching { include("*.svg") })
        customStyleSheets.from(isolated.rootProject.projectDirectory.file("tools/dokka-theme.css"))
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
    compileKotlin {
        mustRunAfter("generateVersion")
    }

    compileTestKotlin {
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }

    shadowJar {
        archiveClassifier = ""
        dependencies {
            exclude(dependency("com.squareup.okio:.*"))
            exclude(dependency("com.ibm.icu:.*"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib:.*"))
        }

        exclude("org/abego/**", "antlr/runtime/**", "**/*.pro")
    }

    register<GenerateVersionTask>("generateVersion") {
        constants.put("__VERSION", project.version.toString())
        constants.put("__GRADLE_COMPAT", providers.gradleProperty("gradle_compat"))
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

            compatibility {
                features {
                    isolatedProjects = true
                    configurationCache = true
                }
            }
        }
    }
}

publishArtifact("stonecutter", "Stonecutter", "Modern Gradle plugin for multi-version management") {
    artifact(tasks.named("slimJar"))
}
