plugins {
    antlr
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("shared-setup")
}

dependencies {
    antlr(libs.antlr)
    api(project("reporter"))
    api(project("replacer"))
    implementation(project("antlr-ext"))
    implementation(common.misc.semver)
    implementation(common.misc.commons)
    implementation(common.kotlin.stdlib)
    implementation(common.kotlin.serialization)

    testImplementation(common.kotest.runner)
    testImplementation(common.kotest.assertions)
}

antlr {
    sourceSets.register("main") {
        visitor = true
        libs.addAll("dev.kikugie.stitcher.antlr", "dev.kikugie.stitcher.antlr.scanner")
    }
}

tasks {
    compileTestKotlin {
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-parameters")
        }
    }
}
