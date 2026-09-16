package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.type.BuiltinTokenType
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import java.io.Serializable

/**
 * An interface for custom code uncommenters.
 *
 * Providing an implementation to [StitcherParameters][dev.kikugie.stitcher.StitcherParameters] enables support for any file format.
 * Implementations **must be stateless** - uncommenters are created once and may run in parallel.
 */
fun interface UncommentingStrategy : Serializable {

    /**
     * Removes comments from the text in the [tracker].
     *
     * The [tokens] sequence provides [BuiltinTokenType.CONTENT], [BuiltinTokenType.COMMENT_OPEN],
     * [BuiltinTokenType.COMMENT_BODY], and [BuiltinTokenType.COMMENT_CLOSE] tokens,
     * which are relative to the [tracker] indexes.
     *
     * The [config] may be used to modify the style of the commented text.
     * It **must not** be used to define what comment syntax to use - only formatting options.
     */
    fun uncomment(config: ConfigurationRegistry, tracker: ModificationTracker, tokens: Sequence<LeafToken>)
}
