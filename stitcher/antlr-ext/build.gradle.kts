plugins {
    antlr
    kotlin("jvm")
    id("shared-setup")
}

dependencies {
    antlr(libs.antlr)
    implementation(common.kotlin.stdlib)
}
