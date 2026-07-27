package dev.kikugie.stitcher.util

interface OpenIntRange : OpenEndRange<Int>, ClosedRange<Int> {
    val end: Int

    override val endExclusive: Int
        get() = end

    override val endInclusive: Int
        get() = end - 1

    fun toIntRange(): IntRange = start..<end

    override fun isEmpty(): Boolean = super<ClosedRange>.isEmpty()
    override fun contains(value: Int): Boolean = super<OpenEndRange>.contains(value)
}