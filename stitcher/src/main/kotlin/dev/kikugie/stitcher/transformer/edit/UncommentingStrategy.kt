package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.token.LeafToken
import java.io.Serializable

fun interface UncommentingStrategy : Serializable {
    fun uncomment(tracker: ModificationTracker, tokens: Sequence<LeafToken>)
}
