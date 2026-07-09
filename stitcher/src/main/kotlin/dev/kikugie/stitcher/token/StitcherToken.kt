package dev.kikugie.stitcher.token

/**
 * Barebones token in the Stitcher AST.
 */
interface StitcherToken {
    val type: StitcherType

    /**
     * The start index of the token in the source file.
     */
    val start: Int

    /**
     * The exclusive end index of the token in the source file.
     */
    val end: Int

    fun interface Visitor<S : StitcherToken, T, C> {
        fun visitToken(token: S, ctx: C): T
    }
}
