@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("jvm")
    id("shared-setup")
    alias(common.plugins.kotlin.dokka)
}

version = property("annotations_version").toString()
val baseVersion: String = property("version").toString()
    .substringBefore('-')
    .let {
        val trimmed = it.substringBeforeLast('.')
        if ('.' in trimmed) trimmed else it
    }

dependencies {
    dokkaHtmlPlugin(common.kotlin.dokka.versioning)
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    abiValidation()
    explicitApi()
    jvmToolchain(17)
}

dokka {
    moduleName = "Stonecutter Annotations"

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
        reportUndocumented = false
        skipEmptyPackages = true

        sourceLink {
            localDirectory = file("src/main/kotlin")
            remoteLineSuffix = "#L"
            remoteUrl("https://codeberg.org/stonecutter/stonecutter/src/branch/$baseVersion/annotations/")
        }

        externalDocumentationLinks.register("kotlin-stdlib") {
            url("https://kotlinlang.org/api/core/")
        }
    }
}

publishArtifact("stonecutter-annotations", "Stonecutter Annotations", "Annotations for Stonecutter parameters")
