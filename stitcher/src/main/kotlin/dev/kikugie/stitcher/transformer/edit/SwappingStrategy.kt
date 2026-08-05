package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker
import java.io.Serializable

fun interface SwappingStrategy : Serializable {
    fun substitute(tracker: ModificationTracker, text: String)
}
