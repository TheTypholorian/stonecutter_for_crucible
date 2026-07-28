package util

import dev.kikugie.stitcher.document.issue.ProblemID
import dev.kikugie.stitcher.document.issue.ProblemReport
import io.kotest.matchers.collections.exist
import io.kotest.matchers.collections.shouldExist
import io.kotest.matchers.shouldNot

infix fun Collection<ProblemReport>.shouldNotHave(id: ProblemID): Unit =
    this shouldNot exist { it.id == id }

infix fun Collection<ProblemReport>.shouldHave(id: ProblemID): Unit =
    shouldExist { it.id == id }

fun Collection<ProblemReport>.shouldHave(id: ProblemID, action: ProblemReport.() -> Unit) {
    shouldExist { report -> (report.id == id).also { if (it) action(report) } }
}
