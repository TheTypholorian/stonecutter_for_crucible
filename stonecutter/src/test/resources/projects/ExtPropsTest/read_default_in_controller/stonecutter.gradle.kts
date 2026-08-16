plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.20.1"


check(stonecutter.properties.contains("prop1")) { "Missing prop1" }
check(!stonecutter.properties.contains("prop2")) { "Resolved prop2" }
