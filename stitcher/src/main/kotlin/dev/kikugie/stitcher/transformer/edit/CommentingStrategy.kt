package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import java.io.Serializable

fun interface CommentingStrategy : Serializable {
    fun comment(tracker: ModificationTracker, tokens: Sequence<LeafToken>)
}
