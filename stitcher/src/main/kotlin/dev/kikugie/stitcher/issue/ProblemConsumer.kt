package dev.kikugie.stitcher.issue

/**
 * Functional interface for handling problems reported by a [ProblemSource].
 */
public fun interface ProblemConsumer {
    public fun accept(report: ProblemReport)
}