package dev.kikugie.stitcher.token.impl

import dev.kikugie.semver.data.VersionPredicate
import dev.kikugie.stitcher.token.StitcherToken

data class PredicateToken(val leaf: LeafToken, val predicate: VersionPredicate) : StitcherToken by leaf
