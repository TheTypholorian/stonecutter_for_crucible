package dev.kikugie.stitcher.token.type

import dev.kikugie.stitcher.antlr.StitcherLexer

@ConsistentCopyVisibility
data class AntlrTokenType private constructor(override val value: Int, override val name: String) : StitcherTokenType {
    companion object {
        private val TYPES: Array<AntlrTokenType> = Array(StitcherLexer.VOCABULARY.maxTokenType) {
            AntlrTokenType(it + 1, StitcherLexer.VOCABULARY.getDisplayName(it + 1))
        }

        @JvmStatic fun of(type: Int): AntlrTokenType = TYPES[type - 1]
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is AntlrTokenType && value == other.value

    override fun hashCode(): Int {
        return value
    }
}
