package dev.kikugie.stitcher.token

/**
 * Barebones token in the Stitcher AST.
 */
interface StitcherToken : OpenEndRange<Int>, ClosedRange<Int> {
    val type: StitcherType

    /**
     * The start index of the token in the source file.
     */
    override val start: Int

    /**
     * The exclusive end index of the token in the source file.
     */
    val end: Int

    override val endExclusive: Int get() = end
    override val endInclusive: Int get() = end - 1

    override fun isEmpty(): Boolean = end <= start
    override fun contains(value: Int): Boolean = value in start..<end

    fun interface Visitor<S : StitcherToken, T, C> {
        fun visitToken(token: S, ctx: C): T
    }
}
