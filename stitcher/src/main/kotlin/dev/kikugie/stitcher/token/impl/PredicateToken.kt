package dev.kikugie.stitcher.token.impl

import dev.kikugie.semver.data.VersionPredicate
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType

public data class PredicateToken(val leaf: LeafToken, val predicate: VersionPredicate) : StitcherToken by leaf
