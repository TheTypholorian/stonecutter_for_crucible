plugins {
    java
}

stonecutter {
    replacements.regex(sc.current.parsed > "1") {
        replace(
            """VERSION\s*=\s*VERSION_1_0""", "VERSION = VERSION_2_0",
            """VERSION\s*=\s*VERSION_2_0""", "VERSION = VERSION_1_0"
        )
        replace(
            """API_[A-Z]+_V1""", "API_MODERN_V2",
            """API_[A-Z]+_V2""", "API_LEGACY_V1"
        )
    }
}