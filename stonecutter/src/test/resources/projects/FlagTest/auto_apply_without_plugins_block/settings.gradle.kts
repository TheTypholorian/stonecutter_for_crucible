pluginManagement {
    repositories {
        maven(file("repo"))
    }
}

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter {
    create(rootProject) {
        versions("1", "2")
    }
}

gradle.beforeProject {
    buildscript.dependencies.add("classpath", "dev.kikugie.test:marker:1.0")
}
