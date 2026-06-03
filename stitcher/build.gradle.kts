import java.net.URI

plugins {
    antlr
    `kotlin-dsl`
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(common.plugins.kotlin.dokka)
    alias(common.plugins.kotlin.dokka.javadoc)
    id("shared-setup")
}

val baseVersion: String = property("version").toString()
    .substringBefore('-').substringBeforeLast('.')

dependencies {
    antlr(libs.antlr)
    api(libs.pcre4j)
    implementation(libs.pcre4j.ffm)
    implementation(libs.pcre4j.bundle)
    implementation(common.misc.semver)
    implementation(common.misc.commons)
    implementation(common.kotlin.stdlib)
    implementation(common.kotlin.serialization)

    testImplementation(common.kotest.runner)
    testImplementation(common.kotest.assertions)
    testImplementation(common.kotlin.serialization.json)

    dokkaHtmlPlugin(common.kotlin.dokka.versioning)
}

antlr {
    sourceSets.create("main") {
        visitor = true
        libs.addAll("dev.kikugie.stitcher.antlr", "dev.kikugie.stitcher.antlr.scanner")
    }
}

dokka {
    moduleName = "Stitcher Processor"

    dokkaPublications.html {
        suppressInheritedMembers = true
        suppressObviousFunctions = true
    }

    pluginsConfiguration.html {
        homepageLink = "https://stonecutter.kikugie.dev/"
        footerMessage = "(c) 2026 KikuGie"
    }

    dokkaSourceSets.named("main") {
        reportUndocumented = false
        skipEmptyPackages = true

        sourceLink {
            localDirectory = file("src/main/kotlin")
            remoteLineSuffix = "#L"
            remoteUrl("https://codeberg.org/stonecutter/stonecutter/src/branch/$baseVersion/stitcher/")
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

tasks {
    compileKotlin {
        mustRunAfter("generateGrammarSource")
    }

    compileTestKotlin {
        mustRunAfter("generateTestGrammarSource")
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }
}
