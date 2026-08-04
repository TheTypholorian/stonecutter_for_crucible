pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.kikugie.dev/releases")
    }

    versionCatalogs {
        create("common") { from("dev.kikugie:stonecutter-versions:1.14.0") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Stonecutter"
includeBuild("build-logic")
include(
    "stonecutter",
    "stitcher", "stitcher:antlr",
    "model"
)