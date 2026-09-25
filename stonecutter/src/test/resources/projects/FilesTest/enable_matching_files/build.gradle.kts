plugins {
    java
}

stonecutter {
    files.disable(sc.current.parsed < "2", listOf("java/Enabled.java"))
}
