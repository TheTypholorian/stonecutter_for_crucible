package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.antlr.StitcherLexer
import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens

private const val INIT_TYPE: Int = Tokens.INVALID + 1

sealed interface StitcherType {
    val value: Int
    val name: String

    companion object {
        fun of(type: Int): StitcherType = when (type) {
            -1 -> EOFStitcherType
            in 0..<INIT_TYPE -> AntlrStitcherType.of(type)
            else -> NativeStitcherType.of(type)
        }
    }
}

data object EOFStitcherType : StitcherType {
    override val value: Int = -1
    override val name: String = "<EOF>"
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
    COND_OPEN, COND_EXT, COND_CLOSE,
    SWAP_OPEN, SWAP_CLOSE, SWAP_LOCAL, SWAP_EXPR,
    REPL_TOGGLE, REPL_LOCAL, REPL_CLOSE, REPL_TOGGLE_ENTRY, REPL_LOCAL_ENTRY,
    GROUP, UNARY, BINARY, CONSTANT, ASSIGNMENT,
    OPERATOR, VERSION, PREDICATE,
    CLOSED_SCOPE, LOOKUP_SCOPE, NAMED_SCOPE;

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
            require(type - INIT_TYPE in entries.indices) { "Token type $type is out of bounds ${INIT_TYPE}..${INIT_TYPE + entries.size - 1}" }
            return entries[type - INIT_TYPE]
        }
    }
}
