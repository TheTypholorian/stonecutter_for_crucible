dependencyResolutionManagement {
    repositories {
        maven("https://maven.kikugie.dev/releases")
    }

    versionCatalogs {
        create("common") { from("dev.kikugie:stonecutter-versions:1.8.0") }
    }
}