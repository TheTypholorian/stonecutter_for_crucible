plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter {
    create(rootProject) {
        versions("26.1", "26.2", "26.3")
        vcsVersion = "26.3"
    }
}