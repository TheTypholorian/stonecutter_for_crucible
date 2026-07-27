plugins {
    antlr
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("shared-setup")
    alias(common.plugins.kotlin.testballoon)
}

dependencies {
    antlr(libs.antlr)
    antlr(project(":stitcher:antlr"))

    api(libs.pcre4j)
    api(project(":model"))

    implementation(libs.pcre4j.ffm)
    implementation(libs.pcre4j.bundle)
    implementation(common.misc.semver)
    implementation(common.misc.commons)

    testImplementation(common.kotlin.testballoon)
    testImplementation(common.kotlin.testballoon.assertions)
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
