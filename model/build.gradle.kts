@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("shared-setup")
    alias(common.plugins.kotlin.testballoon)
}

dependencies {
    implementation(common.misc.semver)
    implementation(common.kotlin.stdlib)
    implementation(common.kotlin.serialization)

    testImplementation(common.kotlin.testballoon)
    testImplementation(common.kotlin.testballoon.assertions)
    testImplementation(common.kotlin.serialization.json)
}

kotlin {
    abiValidation()
    explicitApiWarning()
}
