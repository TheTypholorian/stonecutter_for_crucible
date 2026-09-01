plugins {
    java
}

stonecutter {
    replacements.regex(sc.current.parsed > "1", "!regex_id") {
        replace(
            """LEGACY_ALPHA""", "MODERN_ALPHA",
            """MODERN_ALPHA""", "LEGACY_ALPHA"
        )
    }

    replacements.perl(sc.current.parsed > "1", "!perl_id") {
        replace(
            """OLD_(\w+)""", "NEW_\$1",
            """NEW_(\w+)""", "OLD_\$1"
        )
    }
}