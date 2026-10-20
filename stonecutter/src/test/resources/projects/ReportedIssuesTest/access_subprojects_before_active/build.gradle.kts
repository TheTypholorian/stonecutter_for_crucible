plugins {
    java
}

tasks.register("printSources") {
    val root = rootDir
    val dirs = sourceSets.main.get().java.srcDirs.map { it.relativeTo(root).invariantSeparatorsPath }
    doLast { dirs.forEach { println("srcDir: $it") } }
}
