package dev.kikugie.stitcher.token

import dev.kikugie.antlr.util.endIndex
import dev.kikugie.antlr.util.startIndex
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.token.impl.LeafToken
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

val StitcherToken.length: Int
    inline get() = end - start

fun AntlrToken.toLeafToken(doc: Document, type: StitcherType = StitcherType.of(this.type)): LeafToken =
    LeafToken(doc, type, startIndex, endIndex)

fun TerminalNode.toLeafToken(doc: Document, type: StitcherType = StitcherType.of(symbol.type)): LeafToken =
    LeafToken(doc, type, startIndex, endIndex)

fun ParserRuleContext.toLeafToken(doc: Document, type: StitcherType): LeafToken =
    LeafToken(doc, type, startIndex, endIndex)
