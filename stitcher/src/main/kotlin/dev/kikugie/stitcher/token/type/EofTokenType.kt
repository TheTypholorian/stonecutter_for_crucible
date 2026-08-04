package dev.kikugie.stitcher.token.type

data object EofTokenType : StitcherTokenType {
    override val value: Int = -1
    override val name: String = "<EOF>"
}
