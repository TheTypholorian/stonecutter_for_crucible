@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document

import dev.kikugie.antlr.util.endIndex
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.document.edit.ModificationTracker
import kotlin.io.path.absolutePathString
import kotlin.math.max
import kotlin.math.min

val Document.ancestor: Document
    get() = findAncestor(this)

/**
 * Calculates the smallest acceptable character index for [this] document.
 *
 * Returns `0` for [SourceDocument], or a non-positive value for [InjectedDocument] - an injected
 * window reads backwards into its [parent][InjectedDocument.parent] through negative indices.
 */
fun Document.minIndex(): Int = when (this) {
    is InjectedDocument -> calculateMinOffset(this, 0)
    else -> 0
}

/**
 * Calculates the biggest acceptable exclusive character index for [this] document.
 *
 * Returns [Document.length] for [SourceDocument], or a value past it for [InjectedDocument] -
 * an injected window reads forwards into its [parent][InjectedDocument.parent] through indices
 * beyond its own [length][Document.length].
 */
fun Document.maxIndex(): Int = when (this) {
    is InjectedDocument -> calculateMaxOffset(this, length)
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

inline fun Document.reference(): DocumentReference =
    DocumentReference(this, 0, length)

inline fun Document.reference(start: Int, end: Int = start): DocumentReference =
    DocumentReference(this, start, end)

inline fun Document.reference(range: OpenEndRange<Int>): DocumentReference =
    DocumentReference(this, range.start, range.endExclusive)

inline fun Document.reference(token: AntlrToken): DocumentReference =
    DocumentReference(this, token.startIndex, token.endIndex)

inline fun Document.appendTo(builder: Appendable, range: OpenEndRange<Int>): Unit =
    appendTo(builder, range.start, range.endExclusive)

inline fun DocumentReference.appendTo(builder: Appendable): Unit =
    document.appendTo(builder, this)

infix fun DocumentReference.join(other: DocumentReference): DocumentReference {
    assert(document === other.document) { "Mismatched documents" }
    return copy(start = min(start, other.start), end = max(end, other.end))
}

fun ModificationTracker.stringContent(): String =
    document.substring(start, end)

fun ModificationTracker.charSequenceContent(): CharSequence =
    document.subSequence(start, end)

private tailrec fun findAncestor(doc: Document): Document = when (doc) {
    is InjectedDocument -> findAncestor(doc.parent)
    else -> doc
}

private tailrec fun calculateMinOffset(doc: InjectedDocument, offset: Int): Int = when (val parent = doc.parent) {
    is InjectedDocument -> calculateMinOffset(parent, offset - doc.start)
    else -> offset - doc.start
}

private tailrec fun calculateMaxOffset(doc: InjectedDocument, offset: Int): Int = when (val parent = doc.parent) {
    is InjectedDocument -> calculateMaxOffset(parent, offset + (parent.length - doc.end))
    else -> offset + (parent.length - doc.end)
}
