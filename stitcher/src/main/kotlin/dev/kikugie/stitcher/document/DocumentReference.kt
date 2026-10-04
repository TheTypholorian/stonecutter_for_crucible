package dev.kikugie.stitcher.document

import dev.kikugie.commons.ranges.OpenIntRange

data class DocumentReference(val document: Document, override val start: Int, override val end: Int) : OpenIntRange {
    val location: DocumentLocation by lazy { document.locate(start) }
    val text: String by lazy { if (start < 0) "" else document.substring(start, end) }

    fun toUriString(): String = document.toUriString(location)
}
