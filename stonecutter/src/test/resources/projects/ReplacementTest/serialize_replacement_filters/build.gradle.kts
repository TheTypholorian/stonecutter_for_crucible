plugins {
    java
}

stonecutter {
    replacements {
        string(true) {
            replace("LEGACY_SHARED", "MODERN_SHARED")
        }
        string(true) {
            replace("LEGACY_SCOPED", "MODERN_SCOPED")
            include("**/Scoped.java")
            exclude("**/Other.java")
        }
    }
}
