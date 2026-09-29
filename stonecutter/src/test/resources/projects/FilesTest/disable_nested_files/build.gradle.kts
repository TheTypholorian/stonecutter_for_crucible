plugins {
    java
}

stonecutter {
    files {
        disable(sc.current.parsed >= "2", "java/**/platform/fabric/**")
        disable(sc.current.parsed < "2", "java/**/platform/neoforge/**")
    }
}
