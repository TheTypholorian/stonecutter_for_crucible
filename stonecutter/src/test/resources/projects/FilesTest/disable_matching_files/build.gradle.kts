plugins {
    java
}

stonecutter {
    files {
        disable(sc.current.parsed >= "2", "java/Disabled.java", "resources/data.cfg")
    }
}
