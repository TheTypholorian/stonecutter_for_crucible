package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken

fun interface UncommentingStrategy {
    fun uncomment(tracker: ModificationTracker, tokens: Sequence<LeafToken>)
}
