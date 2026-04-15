package dev.kikugie.stitcher.token.impl

import dev.kikugie.semver.data.VersionPredicate
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType

data class PredicateToken(val operator: LeafToken?, val version: LeafToken, val parsed: VersionPredicate) : StitcherToken {
    override val type: StitcherType get() = NativeStitcherType.PREDICATE
    override val start: Int get() = operator?.start ?: version.start
    override val end: Int get() = version.end
}
