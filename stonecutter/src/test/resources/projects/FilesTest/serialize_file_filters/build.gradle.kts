plugins {
    java
}

stonecutter {
    files {
        exclude("java/Skipped.java")
        disable(true, "java/Disabled.java")
        disable(false, "java/Enabled.java")
    }
}
