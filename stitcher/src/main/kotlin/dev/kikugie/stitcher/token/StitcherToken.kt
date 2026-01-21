package dev.kikugie.stitcher.token

/**
 * Barebones token in the Stitcher AST.
 */
public interface StitcherToken {
    public val type: StitcherType

    /**
     * The start index of the token in the source file.
     */
    public val start: Int

    /**
     * The exclusive end index of the token in the source file.
     */
    public val end: Int
}

public val StitcherToken.length: Int
    inline get() = end - start
