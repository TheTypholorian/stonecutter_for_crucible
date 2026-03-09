plugins {
    java
}

stonecutter {
    replacements.string(sc.current.parsed > "1", "!my_id") {
        replace("abc", "cde")
    }
}