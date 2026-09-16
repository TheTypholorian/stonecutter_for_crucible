@file:Suppress("UnstableApiUsage")

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
        maven("https://maven.kikugie.dev/releases")
    }

    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
        create("common") { from("dev.kikugie:stonecutter-versions:1.16.0") }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
