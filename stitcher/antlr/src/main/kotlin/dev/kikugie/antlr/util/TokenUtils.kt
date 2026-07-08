@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.antlr.util

import dev.kikugie.antlr.MissingRuleContextException
import dev.kikugie.antlr.MissingTerminalNodeException
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.TokenSource
import org.antlr.v4.runtime.WritableToken
import org.antlr.v4.runtime.tree.TerminalNode
import org.antlr.v4.runtime.tree.Tree

/**Returns `true` if this is [Token.EOF].*/
public val Token.isEOF: Boolean
    inline get() = type == Token.EOF

/**Exclusive end index for the token = `stopIndex + 1`.*/
public val Token.endIndex: Int
    inline get() = stopIndex + 1

/**Exclusive end index for the token = `stopIndex + 1`.*/
public var CommonToken.endIndex: Int
    inline get() = stopIndex + 1
    inline set(value) { stopIndex = value - 1 }

/**Zero-indexed line of the token = `line - 1`.*/
public var WritableToken.lineIndex: Int
    inline get() = line - 1
    inline set(value) { line = value + 1 }


/**[Token.getType] of [TerminalNode.getSymbol]*/
public val TerminalNode.type: Int
    inline get() = symbol.type

/**[Token.getStartIndex] of [TerminalNode.getSymbol]*/
public val TerminalNode.startIndex: Int
    inline get() = symbol.startIndex

/**[Token.getStopIndex] of [TerminalNode.getSymbol]*/
public val TerminalNode.stopIndex: Int
    inline get() = symbol.stopIndex

/**[Token.endIndex] of [TerminalNode.getSymbol]*/
public val TerminalNode.endIndex: Int
    inline get() = symbol.endIndex

/**[Token.getLine] of [TerminalNode.getSymbol]*/
public val TerminalNode.line: Int
    inline get() = symbol.line

/**[Token.getCharPositionInLine] of [TerminalNode.getSymbol]*/
public val TerminalNode.charPositionInLine: Int
    inline get() = symbol.charPositionInLine

/**[Token.getChannel] of [TerminalNode.getSymbol]*/
public val TerminalNode.channel: Int
    inline get() = symbol.channel

/**[Token.getTokenSource] of [TerminalNode.getSymbol]*/
public val TerminalNode.tokenSource: TokenSource
    inline get() = symbol.tokenSource

/**[Token.getInputStream] of [TerminalNode.getSymbol]*/
public val TerminalNode.inputStream: CharStream
    inline get() = symbol.inputStream


public fun Tree.childrenSequence(): Sequence<Tree> = sequence {
    for (i in 0 until childCount) yield(getChild(i))
}


/**The start index of this context.*/
public val ParserRuleContext.startIndex: Int
    inline get() = checkNotNull(start) { "ParserRuleContext is empty" }.startIndex

/**The stop index of this context.*/
public val ParserRuleContext.stopIndex: Int
    inline get() = checkNotNull(stop) { "ParserRuleContext is empty" }.stopIndex

/**The end index of this context.*/
public val ParserRuleContext.endIndex: Int
    inline get() = checkNotNull(stop) { "ParserRuleContext is empty" }.endIndex

public inline fun ParserRuleContext.tokenOfType(type: Int, ordinal: Int = 0): TerminalNode =
    getToken(type, ordinal) ?: throw MissingTerminalNodeException(this, type, ordinal)

public inline fun ParserRuleContext.tokenOfTypeOrNull(type: Int, ordinal: Int = 0): TerminalNode? =
    getToken(type, ordinal)

public inline fun <reified T : ParserRuleContext> ParserRuleContext.contextOfType(ordinal: Int = 0): T =
    getRuleContext(T::class.java, ordinal) ?: throw MissingRuleContextException(this, T::class, ordinal)

public inline fun <reified T : ParserRuleContext> ParserRuleContext.contextOfTypeOrNull(ordinal: Int = 0): T? =
    getRuleContext(T::class.java, ordinal)

public inline fun <reified T : ParserRuleContext> ParserRuleContext.contextsOfType(): List<T> =
    getRuleContexts(T::class.java)
