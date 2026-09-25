plugins {
    java
}

stonecutter {
    replacements {
        string(sc.current.parsed > "1", "scoped_id") {
            replace("LEGACY_SCOPED", "MODERN_SCOPED")
            include("**/Scoped.java")
        }
    }
}
