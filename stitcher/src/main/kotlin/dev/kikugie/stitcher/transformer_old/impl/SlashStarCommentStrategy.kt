package dev.kikugie.stitcher.transformer_old.impl

import dev.kikugie.commons.text.countMatching
import dev.kikugie.stitcher.antlr.flex.FlexLexerAdapter
import dev.kikugie.stitcher.antlr.flex.toSequence
import dev.kikugie.stitcher.transformer.strategy.CommentingStrategy
import dev.kikugie.stitcher.transformer.strategy.UncommentingStrategy
import dev.kikugie.stitcher.util.WHITESPACES
import dev.kikugie.stitcher.util.buildString
import dev.kikugie.stitcher.util.hasLineBreak

/**
 * Default commenting strategy for Java and Kotlin files.
 *
 * This implementation determines the comment style on the following criteria:
 * 1. Can the scope be commented in one line? -> Prefix the line with `//`.
 * 2. Does the file need to avoid nested multiline comments? -> Use [StarFlattenCommentStrategy].
 * 3. Otherwise, use [StarPreserveCommentStrategy].
 */
class SlashStarCommentStrategy(private val flatten: Boolean) : CommentingStrategy {
    override fun comment(scope: String, open: Boolean): String = when {
        open -> commentLine(scope)
        flatten -> StarFlattenCommentStrategy.comment(scope, false)
        else -> StarPreserveCommentStrategy.comment(scope, false)
    }

    private fun commentLine(scope: String): String = buildString(scope) {
        insert(scope.countMatching(*WHITESPACES), "//")
    }
}

/**
 * Default commenting strategy for Java files.
 *
 * This implementation wraps the scope with `/* */`,
 * replacing nested multiline comments with `/^ ^/`.
 */
object StarFlattenCommentStrategy : CommentingStrategy {
    override fun comment(scope: String, open: Boolean): String = buildString(scope) {
        var offset = 0
        for (place in scope.findSuperscriptPlaces())
            offset += place.increment(this, offset)
        insert(countMatching(*WHITESPACES), "/*").append("*/")
    }

    @Suppress("unused")
    private fun readResolve(): Any = StarFlattenCommentStrategy
}

/**
 * Default commenting strategy for Kotlin files.
 *
 * This implementation wraps the scope with `/* */` without modifying the contents.
 */
object StarPreserveCommentStrategy : CommentingStrategy {
    override fun comment(scope: String, open: Boolean): String = buildString(scope) {
        insert(countMatching(*WHITESPACES), "/*").append("*/")
    }

    @Suppress("unused")
    private fun readResolve(): Any = StarPreserveCommentStrategy
}

/**
 * Default comment removal strategy for Java and Kotlin files.
 *
 * This implementation unflattens the `/^ ^/` placeholders even for Kotlin comments,
 * as they may have been migrated from older Stonecutter versions.
 *
 * For single-line comments no unflattening is performed.
 */
object StarRemoveCommentStrategy : UncommentingStrategy {
    override fun uncomment(scope: String, opener: String, closer: String): String = buildString(scope) {
        if (opener == "//" || closer.hasLineBreak()) {
            append(closer)
            return@buildString
        }

        var offset = 0
        for (place in scope.findSuperscriptPlaces())
            offset += place.decrement(this, offset)
    }

    @Suppress("unused")
    private fun readResolve(): Any = StarRemoveCommentStrategy
}

internal object SuperscriptMarker

private data class SuperscriptPlace(val markerIndex: Int, val superStart: Int, val superEnd: Int) {
    fun increment(builder: StringBuilder, offset: Int): Int = when (val ch = builder[markerIndex + offset]) {
        '*' -> {
            val pos = markerIndex + offset
            builder.replace(pos, pos + 1, "^")
            0
        }

        '^' -> if (superStart == superEnd) {
            builder.insert(superStart + offset, '¹')
            1
        } else {
            val superscript = builder.substring(superStart + offset, superEnd + offset)
            val incremented = (superscript.superScriptToInt() + 1).toSuperScriptStr()
            builder.replace(superStart + offset, superEnd + offset, incremented)
            incremented.length - superscript.length
        }

        else -> error("Invalid marker $ch at ${markerIndex + offset}")
    }

    fun decrement(builder: StringBuilder, offset: Int): Int = when (val ch = builder[markerIndex + offset]) {
        '*' -> 0
        '^' -> if (superStart == superEnd) {
            val pos = markerIndex + offset
            builder.replace(pos, pos + 1, "*")
            0
        } else {
            val superscript = builder.substring(superStart + offset, superEnd + offset)
            val decremented = (superscript.superScriptToInt() - 1).toSuperScriptStr()
            if (decremented == "⁰") {
                builder.replace(superStart + offset, superEnd + offset, "")
                -superscript.length
            } else {
                builder.replace(superStart + offset, superEnd + offset, decremented)
                decremented.length - superscript.length
            }
        }

        else -> error("Invalid marker $ch at ${markerIndex + offset}")
    }
}

private val SUPERSCRIPT_FACTORY: FlexLexerAdapter.Factory<SuperscriptMarker> = FlexLexerAdapter.Factory { SuperscriptLexer(null) }
private val SUPERSCRIPT_NUMBERS: CharArray = charArrayOf('⁰', '¹', '²', '³', '⁴', '⁵', '⁶', '⁷', '⁸', '⁹')

private fun String.findSuperscriptPlaces(): Sequence<SuperscriptPlace> = SUPERSCRIPT_FACTORY.create(this).toSequence().map {
    val isOpener = get(it.tokenStart) == '/'
    val markerIndex = if (isOpener) it.tokenStart + 1 else it.tokenEnd - 2
    val superStart = if (isOpener) markerIndex + 1 else it.tokenStart
    val superEnd = if (isOpener) it.tokenEnd else markerIndex
    SuperscriptPlace(markerIndex, superStart, superEnd)
}

private fun String.superScriptToInt(): Int {
    var index = 0
    for (ch in this)
        index = index * 10 + SUPERSCRIPT_NUMBERS.indexOf(ch)
    return index
}

private fun Int.toSuperScriptStr(): String = buildString(1) {
    var value = this@toSuperScriptStr
    do {
        append(SUPERSCRIPT_NUMBERS[value % 10])
        value /= 10
    } while (value > 0)
}
