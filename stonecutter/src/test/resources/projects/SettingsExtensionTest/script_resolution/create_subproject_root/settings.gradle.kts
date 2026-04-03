plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter {
    create("example") {
        versions("1", "2")
    }
}