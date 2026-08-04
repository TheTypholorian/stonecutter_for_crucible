package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken

fun interface CommentingStrategy {
    fun comment(tracker: ModificationTracker, tokens: Sequence<LeafToken>)
}
