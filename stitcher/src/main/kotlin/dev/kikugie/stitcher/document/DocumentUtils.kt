@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document

import dev.kikugie.antlr.util.startIndex
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.token.StitcherToken
import org.antlr.v4.runtime.tree.TerminalNode

inline fun Document.locate(token: StitcherToken): DocumentLocation =
    locate(token.start)

inline fun Document.locate(token: AntlrToken): DocumentLocation =
    locate(token.startIndex)

inline fun Document.locate(node: TerminalNode): DocumentLocation =
    locate(node.startIndex)
