package dev.kikugie.stitcher.document

import kotlin.io.path.absolutePathString

/**
 * Calculates the smallest acceptable character index for [this] document.
 *
 * Returns `0` for [SourceDocument], or a non-positive value for [InjectedDocument].
 */
fun Document.minIndex(): Int = when (this) {
    is InjectedDocument -> calculateMinOffset(this, 0)
    else -> 0
}

/**
 * Calculates the biggest acceptable exclusive character index for [this] document.
 *
 * Returns [length] for [SourceDocument], or a non-negative value for [InjectedDocument].
 */
fun Document.maxIndex(): Int = when (this) {
    is InjectedDocument -> calculateMinOffset(this, length)
    else -> length
}

/**
 * Constructs a URI string representation of [this] document's [location].
 *
 * The [location] is represented as `file://{file path}(:{line}:{column})`,
 * where line and column are one-indexed and added if [location] is not [DocumentLocation.UNKNOWN].
 */
fun Document.toUriString(location: DocumentLocation): String = buildString {
    append("file://", file.absolutePathString())

    if (location.isKnown)
        append(':', location.line, ':', location.column)
}

private tailrec fun calculateMinOffset(doc: InjectedDocument, offset: Int): Int = when (val parent = doc.parent) {
    is InjectedDocument -> calculateMinOffset(parent, offset - doc.start)
    else -> offset
}

private tailrec fun calculateMaxOffset(doc: InjectedDocument, offset: Int): Int = when (val parent = doc.parent) {
    is InjectedDocument -> calculateMaxOffset(parent, offset + (parent.length - doc.end))
    else -> offset
}
