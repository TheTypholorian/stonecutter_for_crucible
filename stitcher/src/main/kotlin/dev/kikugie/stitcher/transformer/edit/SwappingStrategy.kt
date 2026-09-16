package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import java.io.Serializable

/**
 * An interface for custom swap handlers.
 *
 * Providing an implementation to [StitcherParameters][dev.kikugie.stitcher.StitcherParameters] enables support for any file format.
 * Implementations **must be stateless** - swappers are created once and may run in parallel.
 */
fun interface SwappingStrategy : Serializable {

    /**
     * Substitutes the contents of the [tracker] with the provided [text].
     *
     * The implementation may take the text assigned to the [tracker]
     * to determine indentation for the new [text].
     *
     * The [config] may be used to alter the formatting of the output.
     */
    fun substitute(config: ConfigurationRegistry, tracker: ModificationTracker, text: String)
}
