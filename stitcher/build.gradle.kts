plugins {
    antlr
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("shared-setup")
}

dependencies {
    antlr(libs.antlr)
    implementation(common.misc.semver)
    implementation(common.misc.commons)
    implementation(common.kotlin.stdlib)
    implementation(common.kotlin.serialization)

    testImplementation(common.kotest.runner)
    testImplementation(common.kotest.assertions)
    testImplementation(common.kotlin.serialization.json)
}

antlr {
    sourceSets.create("main") {
        visitor = true
        libs.addAll("dev.kikugie.stitcher.antlr", "dev.kikugie.stitcher.antlr.scanner")
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
