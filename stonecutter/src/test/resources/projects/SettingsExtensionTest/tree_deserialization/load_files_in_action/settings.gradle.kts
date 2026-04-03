plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter {
    create(rootProject) {
        load(file("versions.json"))
    }
}