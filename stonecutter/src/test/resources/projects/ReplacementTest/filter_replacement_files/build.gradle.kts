plugins {
    java
}

stonecutter {
    replacements {
        string(sc.current.parsed > "1") {
            replace("LEGACY_SCOPED", "MODERN_SCOPED")
            include("**/Scoped.java")
        }
        string(sc.current.parsed > "1") {
            replace("LEGACY_OTHER", "MODERN_OTHER")
            exclude("**/Scoped.java")
        }
        string(sc.current.parsed > "1") {
            replace("LEGACY_SHARED", "MODERN_SHARED")
        }
    }
}
