plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter {
    create(rootProject) {
        versions("1", "2")
        vcsVersion = "2"
    }
}
