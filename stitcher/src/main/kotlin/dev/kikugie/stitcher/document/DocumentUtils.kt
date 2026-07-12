@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document

import dev.kikugie.antlr.util.startIndex
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.token.StitcherToken
import org.antlr.v4.runtime.tree.TerminalNode
import kotlin.io.path.absolutePathString

inline fun Document.locate(token: StitcherToken): DocumentLocation =
    locate(token.start)

inline fun Document.locate(token: AntlrToken): DocumentLocation =
    locate(token.startIndex)

inline fun Document.locate(node: TerminalNode): DocumentLocation =
    locate(node.startIndex)

fun Document.min(): Int = when (this) {
    is InjectedDocument -> getMinOffset(this, 0)
    else -> 0
}

fun Document.max(): Int = when (this) {
    is InjectedDocument -> getMaxOffset(this, length)
    else -> length
}

fun Document.link(loc: DocumentLocation): String = when (loc.packed) {
    -1L -> "file://${path.absolutePathString()}"
    else -> "file://${path.absolutePathString()}:${loc.line}:${loc.column}"
}

fun Document.lineAt(index: Int): IntRange {
    val location = locate(index)
    if (!location.isKnown) return IntRange.EMPTY

    val startIndex = index(location.lineIndex, 0)
    val endIndex = index(location.lineIndex, Int.MAX_VALUE)
    return startIndex until endIndex
}

private tailrec fun getMinOffset(document: InjectedDocument, offset: Int): Int = when (val it = document.parent) {
    is InjectedDocument -> getMinOffset(it, offset - document.start)
    else -> offset
}

private tailrec fun getMaxOffset(document: InjectedDocument, offset: Int): Int = when (val it = document.parent) {
    is InjectedDocument -> getMaxOffset(it, offset + (it.length - document.end))
    else -> offset
}
