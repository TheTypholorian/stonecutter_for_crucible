plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter tasks {
    order("compileJava")
}

stonecutter active "3"
