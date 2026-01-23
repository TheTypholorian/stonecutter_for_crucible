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
}

val StitcherToken.length: Int
    inline get() = end - start
