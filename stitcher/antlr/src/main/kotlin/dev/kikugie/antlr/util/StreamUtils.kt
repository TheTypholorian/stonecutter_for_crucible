package dev.kikugie.antlr.util

import org.antlr.v4.runtime.*

public const val EOF_CHAR: Char = '\u0000'


/**The size of this stream **without** [IntStream.EOF].*/
public val IntStream.length: Int
    inline get() = size() - 1

/**Gets the next value in the stream and advances it. Doesn't consume [IntStream.EOF].*/
public fun IntStream.advance(): Int =
    LA(1).also { if (it != IntStream.EOF) consume() }

/**Gets the next character in the stream and advances it. Doesn't consume [EOF_CHAR].*/
public fun CharStream.advance(): Char =
    LC(1).also { if (it != EOF_CHAR) consume() }

/**Gets the next token in the stream and advances it. Doesn't consume [Token.EOF].*/
public fun TokenStream.advance(): Token =
    LT(1).also { if (!it.isEOF) consume() }

/**Gets the character at the given offset in the stream.*/
public fun CharStream.LC(i: Int): Char = when (val int = LA(i)) {
    IntStream.EOF -> EOF_CHAR
    else -> int.toChar()
}

/**Creates a sequence of all tokens in the source, except for [Token.EOF].*/
public fun TokenSource.toSequence(): Sequence<Token> =
    generateSequence { nextToken()?.takeUnless(Token::isEOF) }

/**Creates a sequence of all tokens in the stream, except for [Token.EOF].*/
public fun TokenStream.toSequence(): Sequence<Token> =
    generateSequence { LT(1).takeUnless(Token::isEOF)?.also { consume() } }
