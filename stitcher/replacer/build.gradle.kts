plugins {
    java
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("shared-setup")
}

dependencies {
    implementation(project(":stitcher:antlr-ext"))
    implementation(project(":stitcher:reporter"))
    implementation(common.kotlin.stdlib)
    implementation(common.kotlin.serialization)

    testImplementation(common.kotest.runner)
    testImplementation(common.kotest.assertions)
}
