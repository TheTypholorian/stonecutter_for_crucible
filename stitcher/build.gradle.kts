import java.net.URI

plugins {
    antlr
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(common.plugins.kotlin.testballoon)
    alias(common.plugins.kotlin.dokka)
    alias(common.plugins.kotlin.dokka.javadoc)
    id("shared-setup")
}

val baseVersion: String = property("version").toString()
    .substringBefore('-')
    .let {
        val trimmed = it.substringBeforeLast('.')
        if ('.' in trimmed) trimmed else it
    }

dependencies {
    antlr(libs.antlr)
    antlr(project(":stitcher:antlr"))

    api(libs.pcre4j)
    api(project(":model"))

    implementation(libs.pcre4j.ffm)
    implementation(libs.pcre4j.bundle)
    implementation(common.misc.semver)
    api(common.misc.commons)

    testImplementation(common.kotlin.testballoon)
    testImplementation(common.kotlin.testballoon.assertions)

    dokkaHtmlPlugin(common.kotlin.dokka.versioning)
}

tasks {
    generateGrammarSource {
        source = fileTree(layout.projectDirectory.dir("src/main/antlr")) {
            include("**/*.g4")
        }

        outputDirectory = layout.buildDirectory.dir("generated/antlr/main/dev/kikugie/stitcher/antlr").get().asFile
        packageName = "dev.kikugie.stitcher.antlr"
        arguments = listOf("-visitor", "-no-listener", "-Dlanguage=Kotlin")
    }

    sourceSets.main {
        java.srcDir(generateGrammarSource)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xwhen-expressions=indy")
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

        customAssets.from(isolated.rootProject.projectDirectory.dir("tools/dokka-icons").asFileTree.matching { include("*.svg") })
        customStyleSheets.from(isolated.rootProject.projectDirectory.file("tools/dokka-theme.css"))
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
