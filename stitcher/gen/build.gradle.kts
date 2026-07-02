plugins {
    kotlin("jvm")
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
    jvmToolchain(21)
    explicitApiWarning()
}
