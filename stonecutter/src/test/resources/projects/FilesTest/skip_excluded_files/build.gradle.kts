plugins {
    java
}

stonecutter {
    files {
        exclude("Excluded.java")
        disable(true, "Excluded.java")
    }
}
