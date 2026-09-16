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
            include("java/**")
        }
    }
}

stonecutter.process(rootDir.resolve("src/main/resources/data.txt"), "build/processed.txt")
