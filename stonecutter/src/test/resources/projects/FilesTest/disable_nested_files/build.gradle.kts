plugins {
    java
}

stonecutter {
    files {
        disable(sc.current.parsed >= "2", "**/platform/fabric/**")
        disable(sc.current.parsed < "2", "**/platform/neoforge/**")
    }
}
