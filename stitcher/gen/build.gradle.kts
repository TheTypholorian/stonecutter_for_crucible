plugins {
    kotlin("jvm")
    id("shared-setup")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.antlr)
    implementation(common.kotlin.stdlib)
}

java {
    withSourcesJar()
}

kotlin {
    explicitApiWarning()
}
