package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.ModificationTracker
import dev.kikugie.stitcher.token.impl.LeafToken
import java.io.Serializable as JavaSerializable

fun interface CommentingStrategy : JavaSerializable {
    fun comment(tracker: ModificationTracker, tokens: Sequence<LeafToken>)
}

fun interface UncommentingStrategy : JavaSerializable {
    fun uncomment(tracker: ModificationTracker, tokens: Sequence<LeafToken>)
}

fun interface SwappingStrategy : JavaSerializable {
    fun substitute(tracker: ModificationTracker, text: String)
}
