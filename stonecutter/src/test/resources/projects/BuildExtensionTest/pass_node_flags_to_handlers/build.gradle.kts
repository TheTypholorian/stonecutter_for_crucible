import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins {
    java
}

stonecutter {
    swaps["entry"] = "warn"
}

stonecutter flags {
    StonecutterFlag.WARN_DUPLICATE_PROPERTIES = true
}

stonecutter.process(rootDir.resolve("config/data.cfg"), "build/processed.cfg")
