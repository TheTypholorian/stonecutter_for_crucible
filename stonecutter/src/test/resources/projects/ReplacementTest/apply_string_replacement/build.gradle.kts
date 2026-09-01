plugins {
    java
}

stonecutter {
    replacements.string(sc.current.parsed > "1") {
        replace("LEGACY_NAME", "MODERN_NAME")
    }
}