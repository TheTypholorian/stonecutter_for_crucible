@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.document.issue

@JvmInline @JvmExposeBoxed @OptIn(ExperimentalStdlibApi::class)
value class ProblemID(val id: String) {
    inline operator fun plus(child: String): ProblemID = ProblemID("$id:$child")
    override fun toString(): String = id
}