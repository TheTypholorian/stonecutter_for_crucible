@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import java.net.URI

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("shared-setup")
    alias(common.plugins.kotlin.dokka)
    alias(common.plugins.kotlin.testballoon)
}

val baseVersion: String = property("version").toString()
    .substringBefore('-')
    .let {
        val trimmed = it.substringBeforeLast('.')
        if ('.' in trimmed) trimmed else it
    }

dependencies {
    implementation(common.misc.semver)
    implementation(common.kotlin.stdlib)
    implementation(common.kotlin.serialization)

    testImplementation(common.kotlin.testballoon)
    testImplementation(common.kotlin.testballoon.assertions)
    testImplementation(common.kotlin.serialization.json)

    dokkaHtmlPlugin(common.kotlin.dokka.versioning)
}

kotlin {
    abiValidation()
    explicitApiWarning()
}

dokka {
    moduleName = "Stonecutter Model"

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
            remoteUrl("https://codeberg.org/stonecutter/stonecutter/src/branch/$baseVersion/model/")
        }

        externalDocumentationLinks.register("kotlin-stdlib") {
            url("https://kotlinlang.org/api/core/")
        }

        externalDocumentationLinks.register("kotlinx-serialization") {
            url("https://kotlinlang.org/api/kotlinx.serialization/")
        }

        externalDocumentationLinks.register("semver") {
            val version = common.versions.misc.semver.get()
            url("https://javadoc.io/static/dev.kikugie/semver/$version")
            packageListUrl.set(URI.create("https://javadoc.io/static/dev.kikugie/semver/$version/semver/package-list"))
        }
    }
}
