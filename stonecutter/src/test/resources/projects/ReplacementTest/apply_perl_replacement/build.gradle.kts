plugins {
    java
}

stonecutter {
    replacements.perl(sc.current.parsed > "1") {
        replace(
            """MOD_(\w+)_LEGACY""", "MOD_\$1_MODERN",
            """MOD_(\w+)_MODERN""", "MOD_\$1_LEGACY"
        )
    }
}