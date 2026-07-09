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

fun Document.link(loc: DocumentLocation): String = when (loc.packed) {
    -1L -> "file://${path.absolutePathString()}"
    else -> "file://${path.absolutePathString()}:${loc.line}:${loc.column}"
}

fun Document.fullText(): String =
    get(0, length)
