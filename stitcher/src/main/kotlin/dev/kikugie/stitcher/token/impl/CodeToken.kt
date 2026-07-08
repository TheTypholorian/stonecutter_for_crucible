package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType

public data class CodeToken(val marker: LeafToken, val definition: DefinitionToken) : StitcherToken {
    override val type: StitcherType get() = NativeStitcherType.CODE_DEF
    override val start: Int get() = marker.start
    override val end: Int get() = definition.end
}