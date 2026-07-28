plugins {
    kotlin("jvm")
    id("shared-setup")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.antlr)
}

kotlin {
    explicitApiWarning()
}
