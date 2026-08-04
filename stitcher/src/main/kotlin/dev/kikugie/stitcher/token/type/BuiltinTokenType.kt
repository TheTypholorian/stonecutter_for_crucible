package dev.kikugie.stitcher.token.type

import dev.kikugie.stitcher.antlr.StitcherLexer

private val OFFSET: Int = StitcherLexer.VOCABULARY.maxTokenType + 1

enum class BuiltinTokenType : StitcherTokenType {
    ROOT, CODE, CONTENT, COMMENT, COMMENT_OPEN, COMMENT_BODY, COMMENT_CLOSE,
    COND_OPEN, COND_EXT, COND_CLOSE,
    SWAP_OPEN, SWAP_CLOSE, SWAP_LOCAL, SWAP_EXPR,
    REPL_TOGGLE, REPL_LOCAL, REPL_CLOSE, REPL_TOGGLE_ENTRY, REPL_LOCAL_ENTRY,
    GROUP, UNARY, BINARY, CONSTANT, ASSIGNMENT, PREDICATE,
    CLOSED_SCOPE, LOOKUP_SCOPE, NAMED_SCOPE;

    override val value: Int = OFFSET + ordinal
    override fun toString(): String = "NativeStitcherType(name=$name, value=$value)"

    companion object {
        @JvmStatic fun of(type: Int): BuiltinTokenType {
            return entries[type - OFFSET]
        }
    }
}