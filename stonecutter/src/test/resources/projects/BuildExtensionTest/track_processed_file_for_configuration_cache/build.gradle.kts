plugins {
    java
}

stonecutter {
    replacements {
        string(true) {
            replace("LEGACY_NAME", "MODERN_NAME")
        }
    }
}

stonecutter.process(rootDir.resolve("config/data.txt"), "build/processed.txt")
