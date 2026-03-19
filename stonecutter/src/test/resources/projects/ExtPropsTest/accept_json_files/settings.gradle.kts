plugins {
    id("dev.kikugie.stonecutter")
}

sc.properties(file("stonecutter.properties.json"))

stonecutter {
    create(rootProject) {
        versions("1.20.1")
    }
}