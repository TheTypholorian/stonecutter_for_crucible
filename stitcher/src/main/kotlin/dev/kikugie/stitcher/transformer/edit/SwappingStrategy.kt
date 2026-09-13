package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import java.io.Serializable

fun interface SwappingStrategy : Serializable {
    fun substitute(config: ConfigurationRegistry, tracker: ModificationTracker, text: String)
}
