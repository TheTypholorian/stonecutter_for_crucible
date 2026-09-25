plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1"

stonecutter handlers {
    configure("cfg") {
        commenter.set(line("#"))
    }
}
