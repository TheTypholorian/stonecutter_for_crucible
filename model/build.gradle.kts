@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("shared-setup")
}

dependencies {
    implementation(common.misc.semver)
    implementation(common.kotlin.stdlib)
    implementation(common.kotlin.serialization)

    testImplementation(common.kotest.runner)
    testImplementation(common.kotest.assertions)
    testImplementation(common.kotlin.serialization.json)
}

kotlin {
    abiValidation()
    explicitApiWarning()
}
