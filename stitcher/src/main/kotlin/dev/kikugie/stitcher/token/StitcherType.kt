package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.antlr.StitcherLexer

private const val INIT_TYPE = StitcherLexer.WHITESPACE + 1

sealed interface StitcherType {
    val value: Int
    val name: String

    companion object {
        fun of(type: Int): StitcherType =
            if (type < INIT_TYPE) AntlrStitcherType.of(type)
            else NativeStitcherType.of(type)
    }
}

@ConsistentCopyVisibility
data class AntlrStitcherType private constructor(override val value: Int, override val name: String) : StitcherType {
    companion object {
        private val types: Array<AntlrStitcherType> = Array(StitcherLexer.VOCABULARY.maxTokenType) { i ->
            AntlrStitcherType(i + 1, StitcherLexer.VOCABULARY.getDisplayName(i + 1))
        }

        @JvmStatic fun of(type: Int): AntlrStitcherType {
            require(type in 1..types.size) { "Token type $type is out of bounds 1..${types.size}" }
            return types[type - 1]
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
enum class NativeStitcherType : StitcherType {
    ROOT, CODE, CONTENT, COMMENT, COMMENT_OPEN, COMMENT_BODY, COMMENT_CLOSE,
    SWAP_OPEN, SWAP_CLOSE, COND_OPEN, COND_EXT, COND_CLOSE, REPL,
    GROUP, UNARY, BINARY, CONSTANT, ASSIGNMENT,
    CLOSED_SCOPE, LOOKUP_SCOPE, PREDICATE;

    override val value: Int get() = INIT_TYPE + ordinal
    override fun toString(): String = "NativeStitcherType(name=$name, value=$value)"
    inline operator fun component1(): Int = value
    inline operator fun component2(): String = name

    companion object {
        init {
            check(INIT_TYPE == StitcherLexer.VOCABULARY.maxTokenType + 1) {
                "INIT_TYPE is expected to be ${StitcherLexer.VOCABULARY.maxTokenType + 1}, got $INIT_TYPE"
            }
        }

        @JvmStatic fun of(type: Int): NativeStitcherType {
            require(type - INIT_TYPE in 0..<entries.size) { "Token type $type is out of bounds ${INIT_TYPE}..${INIT_TYPE + entries.size - 1}" }
            return entries[type - INIT_TYPE]
        }
    }
}
