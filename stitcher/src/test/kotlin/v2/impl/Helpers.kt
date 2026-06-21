package v2.impl

import dev.kikugie.stitcher.issue.ProblemReport

val Iterable<ProblemReport>.ids
    get() = asSequence().map(ProblemReport::id)