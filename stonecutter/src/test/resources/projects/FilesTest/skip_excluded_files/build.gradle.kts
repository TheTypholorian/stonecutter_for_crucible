plugins {
    java
}

stonecutter {
    files {
        exclude("java/Excluded.java")
        disable(true, "java/Excluded.java")
    }
}
