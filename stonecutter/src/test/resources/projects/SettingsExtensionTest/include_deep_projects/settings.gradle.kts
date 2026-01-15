plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter {
    create("subproject") {
        versions("1")
        branch("branch")
    }
}