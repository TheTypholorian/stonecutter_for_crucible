plugins {
    java
}

stonecutter.process(rootDir.resolve("config/data.txt"), "build/processed.txt")
