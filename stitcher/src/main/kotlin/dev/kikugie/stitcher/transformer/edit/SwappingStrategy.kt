package dev.kikugie.stitcher.transformer.edit

import dev.kikugie.stitcher.document.edit.ModificationTracker

fun interface SwappingStrategy {
    fun substitute(tracker: ModificationTracker, text: String)
}
