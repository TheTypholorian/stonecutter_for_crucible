import dev.kikugie.stitcher.transformer.edit.SwappingStrategy
import dev.kikugie.stitcher.transformer.edit.impl.StandardSwappingStrategy

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1"

stonecutter handlers {
    configure("cfg") {
        commenter.set(line("#"))
        swapper.set(SwappingStrategy { config, tracker, text -> StandardSwappingStrategy.substitute(config, tracker, text.uppercase()) })
    }
}
