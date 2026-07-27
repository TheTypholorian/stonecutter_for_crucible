plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(common.plugins.kotlin.testballoon)
}

dependencies {
    implementation(project(":stitcher:common"))

    testImplementation(common.kotlin.testballoon)
    testImplementation(common.kotlin.testballoon.assertions)
}
