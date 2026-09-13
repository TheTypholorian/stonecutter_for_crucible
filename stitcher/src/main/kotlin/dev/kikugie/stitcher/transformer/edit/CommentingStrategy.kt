package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import java.io.Serializable

/**
 * An interface for custom code commenters.
 *
 * Providing an implementation to [StitcherParameters][dev.kikugie.stitcher.StitcherParameters]
 * enables support for any file format.
 * Implementations **must be stateless** - commenters are created once
 * and may run in parallel.
 */
fun interface CommentingStrategy : Serializable {
    fun comment(config: ConfigurationRegistry, tracker: ModificationTracker, tokens: Sequence<LeafToken>)
}
