package dev.kikugie.stitcher.token.type

import dev.kikugie.stitcher.antlr.StitcherLexer

sealed interface StitcherTokenType {
    val value: Int
    val name: String

    companion object {
        @JvmStatic fun of(type: Int): StitcherTokenType = when (type) {
            -1 -> EofTokenType
            in 0..StitcherLexer.VOCABULARY.maxTokenType -> AntlrTokenType.of(type)
            else -> BuiltinTokenType.of(type)
        }
    }
}
