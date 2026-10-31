package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.DefinitionToken
import dev.kikugie.stitcher.token.LeafToken
import dev.kikugie.stitcher.token.type.BuiltinTokenType
import dev.kikugie.stonecutter.model.process.ConfigurationRegistry
import java.io.Serializable

/**
 * An interface for custom code commenters.
 *
 * Providing an implementation to [StitcherParameters][dev.kikugie.stitcher.StitcherParameters] enables support for any file format.
 * Implementations **must be stateless** - commenters are created once and may run in parallel.
 */
fun interface CommentingStrategy : Serializable {

    /**
     * Comments out the text segment within the [tracker].
     *
     * The implementation should insert comment markers in the text using the
     * [ModificationTracker], and escape contents in-between if necessary.
     * Tracker indexes are stable, so inserted or removed text segments don't affect its length.
     *
     * The [tokens] sequence may be used to identify nested comments within the text.
     * It provides four types of tokens: [BuiltinTokenType.CONTENT], [BuiltinTokenType.COMMENT_OPEN],
     * [BuiltinTokenType.COMMENT_BODY], [BuiltinTokenType.COMMENT_CLOSE],
     * which are relative to [tracker] indexes.
     *
     * The [config] may be used to modify the style of the commented text.
     * It **must not** be used to define what comment syntax to use - only formatting options.
     */
    fun comment(config: ConfigurationRegistry, tracker: ModificationTracker, tokens: Sequence<LeafToken>)

    /**
     * Comments out the text segment within the [tracker].
     *
     * The implementation should insert comment markers in the text using the
     * [ModificationTracker], and escape contents in-between if necessary.
     * Tracker indexes are stable, so inserted or removed text segments don't affect its length.
     *
     * The [tokens] sequence may be used to identify nested comments within the text.
     * It provides four types of tokens: [BuiltinTokenType.CONTENT], [BuiltinTokenType.COMMENT_OPEN],
     * [BuiltinTokenType.COMMENT_BODY], [BuiltinTokenType.COMMENT_CLOSE],
     * which are relative to [tracker] indexes.
     *
     * The [config] may be used to modify the style of the commented text.
     * It **must not** be used to define what comment syntax to use - only formatting options.
     *
     * The [body] parameter may also be used to decide the comment style.
     * This method falls back to the 3-parameter override.
     */
    fun comment(config: ConfigurationRegistry, tracker: ModificationTracker, tokens: Sequence<LeafToken>, body: DefinitionToken.Body?): Unit =
        comment(config, tracker, tokens)
}
