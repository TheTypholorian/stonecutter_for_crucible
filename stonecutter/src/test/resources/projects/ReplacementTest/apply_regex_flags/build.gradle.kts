plugins {
    java
}

stonecutter {
    replacements.regex(sc.current.parsed > "1") {
        replace(
            """legacy_marker""", "MODERN_MARKER",
            """modern_marker""", "LEGACY_MARKER",
            RegexOption.IGNORE_CASE
        )
    }
}