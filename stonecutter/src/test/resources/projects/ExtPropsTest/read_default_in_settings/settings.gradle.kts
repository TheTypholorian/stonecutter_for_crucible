plugins {
    id("dev.kikugie.stonecutter")
}

check(stonecutter.properties.contains("prop1")) { "Missing prop1" }
check(!stonecutter.properties.contains("prop2")) { "Resolved prop2" }
