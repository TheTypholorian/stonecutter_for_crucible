package dev.kikugie.stitcher.transformer_old.strategy

import java.io.Serializable

/**
 * Defines a strategy for adding comments to a given scope of text.
 */
public fun interface CommentingStrategy : Serializable {
    /**
     * Adds comments to the specified [scope].
     *
     * The implementation should take nested comments and indentation
     * of the scope into consideration.
     */
    public fun comment(scope: String, open: Boolean): String
}