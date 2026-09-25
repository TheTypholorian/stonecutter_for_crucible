plugins {
    java
}

stonecutter {
    replacements {
        string(sc.current.parsed > "1", "!scoped") {
            replace("LEGACY_SCOPED", "MODERN_SCOPED")
            include("**/Scoped.java")
        }
    }
}
