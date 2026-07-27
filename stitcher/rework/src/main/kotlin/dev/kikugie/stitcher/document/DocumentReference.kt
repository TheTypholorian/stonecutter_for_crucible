package dev.kikugie.stitcher.document

import dev.kikugie.stitcher.util.OpenIntRange

data class DocumentReference(val document: Document, override val start: Int, override val end: Int) : OpenIntRange {
    val location: DocumentLocation by lazy { document.locate(start) }

    fun toUriString(): String = document.toUriString(location)
}
