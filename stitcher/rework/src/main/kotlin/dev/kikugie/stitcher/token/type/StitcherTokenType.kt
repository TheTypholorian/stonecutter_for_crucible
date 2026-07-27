package dev.kikugie.stitcher.token.type

sealed interface StitcherTokenType {
    val value: Int
    val name: String
}
