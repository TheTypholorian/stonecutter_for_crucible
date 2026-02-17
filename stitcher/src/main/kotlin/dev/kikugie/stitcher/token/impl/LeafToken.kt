package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType

data class LeafToken(
    override val type: StitcherType,
    override val start: Int,
    override val end: Int,
    val text: String,
) : StitcherToken
