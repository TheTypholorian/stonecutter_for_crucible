import dev.kikugie.stitcher.transformer.edit.SwappingStrategy
import dev.kikugie.stitcher.transformer.edit.impl.StandardSwappingStrategy
import dev.kikugie.stonecutter.model.process.ConfigurationFlag

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1"

stonecutter handlers {
    configure("cfg") {
        swapper.set(SwappingStrategy { config, tracker, text ->
            val warn = config[ConfigurationFlag("warn_duplicate_properties", false)]
            StandardSwappingStrategy.substitute(config, tracker, "$text=$warn")
        })
    }
}
