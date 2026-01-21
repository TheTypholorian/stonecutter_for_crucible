package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.antlr.StitcherLexer
import dev.kikugie.stitcher.util.lastNotNull
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType

public sealed interface ScopeToken : StitcherToken {
    public data class Closed(val marker: LeafToken) : ScopeToken, StitcherToken by marker {
        override val type: StitcherType get() = StitcherType.Custom.CLOSED_SCOPE
    }

    public data class Lookup(val marker: LeafToken, val plus: LeafToken? = null, val lookup: LeafToken? = null) : ScopeToken {
        override val type: StitcherType get() = StitcherType.Custom.LOOKUP_SCOPE
        override val start: Int get() = marker.start
        override val end: Int = lastNotNull(marker, plus, lookup).end

        public fun isCapturing(): Boolean = plus != null
        public fun extractLookup(): String = when(lookup?.type?.takeIf { it.source == StitcherType.Leaf }?.value) {
                StitcherLexer.IDENTIFIER -> lookup.text
                StitcherLexer.QUOTED -> lookup.text.removeSurrounding("'")
                else -> error("Invalid type ${lookup?.type}")
            }
    }
}
