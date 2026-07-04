plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("shared-setup")
}

dependencies {
    implementation(common.misc.semver)
    implementation(common.kotlin.stdlib)
    implementation(common.kotlin.serialization)
}
