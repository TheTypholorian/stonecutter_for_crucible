package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.antlr.StitcherLexer
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.util.lastNotNull

sealed interface ScopeToken : StitcherToken {
    data class Closed(val marker: LeafToken) : ScopeToken, StitcherToken by marker {
        override val type: NativeStitcherType get() = NativeStitcherType.CLOSED_SCOPE
    }

    data class Lookup(val marker: LeafToken, val plus: LeafToken? = null, val lookup: LeafToken? = null) : ScopeToken {
        override val type: NativeStitcherType get() = NativeStitcherType.LOOKUP_SCOPE
        override val start: Int get() = marker.start
        override val end: Int = lastNotNull(marker, plus, lookup).end

        fun isCapturing(): Boolean = plus != null
        fun extractLookup(): String = when (lookup?.type?.value) {
            StitcherLexer.IDENTIFIER -> lookup.text
            StitcherLexer.QUOTED -> lookup.text.removeSurrounding("'")
            else -> error("Invalid type ${lookup?.type}")
        }
    }
}
