plugins {
    java
}

stonecutter {
    replacements.regex(sc.current.parsed > "1") {
        replace("LEGACY_[A-Z]+_NAME", "MODERN_NAME")
        reverse("MODERN_NAME", "LEGACY_TEST_NAME")
    }
}