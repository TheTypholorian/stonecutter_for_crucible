package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import java.io.Serializable

fun interface UncommentingStrategy : Serializable {
    fun uncomment(config: ConfigurationRegistry, tracker: ModificationTracker, tokens: Sequence<LeafToken>)
}
