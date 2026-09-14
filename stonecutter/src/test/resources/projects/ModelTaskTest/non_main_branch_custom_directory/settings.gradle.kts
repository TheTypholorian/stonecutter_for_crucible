plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter {
    create(rootProject) {
        versions("1")
        branch("experimental") {
            versions("1")
        }
    }
}
