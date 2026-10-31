plugins {
    java
}

stonecutter {
    files {
        disable(sc.current.parsed >= "2", "Disabled.java", "data.cfg")
    }
}
