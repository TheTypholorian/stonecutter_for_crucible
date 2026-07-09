package dev.kikugie.stitcher.token

import dev.kikugie.antlr.util.endIndex
import dev.kikugie.antlr.util.startIndex
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.token.impl.LeafToken
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.TerminalNode

internal val LAYOUT_GROUP: ProblemID = ProblemID("file-layout")
internal val SYNTAX_GROUP: ProblemID = ProblemID("comment-syntax")

val StitcherToken.length: Int
    inline get() = end - start

fun AntlrToken.toLeafToken(doc: Document, type: StitcherType = StitcherType.of(this.type), text: String? = null): LeafToken =
    LeafToken(doc, type, startIndex, endIndex).apply { if (text != null) this.text = text }

fun TerminalNode.toLeafToken(doc: Document, type: StitcherType = StitcherType.of(symbol.type), text: String? = null): LeafToken =
    LeafToken(doc, type, startIndex, endIndex).apply { if (text != null) this.text = text }

fun ParserRuleContext.toLeafToken(doc: Document, type: StitcherType, text: String? = null): LeafToken =
    LeafToken(doc, type, startIndex, endIndex).apply { if (text != null) this.text = text }
