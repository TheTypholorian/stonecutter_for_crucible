package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.token.type.StitcherTokenType

interface StitcherToken {
    val type: StitcherTokenType

    fun interface Visitor<S : StitcherToken, T, C> {
        fun visitToken(token: S, ctx: C): T
    }
}
