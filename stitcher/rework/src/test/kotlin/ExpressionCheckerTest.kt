@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.document.issue.ProblemReport
import dev.kikugie.stitcher.document.issue.SYNTAX_GROUP
import dev.kikugie.stitcher.transformer.ExpressionChecker
import dev.kikugie.stonecutter.model.process.ProcessParameters
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import matcher.parseSnippet
import matcher.vars
import org.intellij.lang.annotations.Language
import util.shouldHave

val ExpressionCheckerTest by testSuite {
    test("unresolved swap id") {
        "//$ swap_id".shouldHaveProblems(vars()) {
            shouldHave(SYNTAX_GROUP + "unresolved-swap-id") {
                label shouldContain "swap_id"
                hint shouldNotContain "Did you mean"
                hint shouldNotContain "Available swaps"
            }
        }
    }

    test("unresolved swap id with listing") {
        val params = vars {
            swaps["my_swap"] = "..."
        }
        "//$ swap_id".shouldHaveProblems(params) {
            shouldHave(SYNTAX_GROUP + "unresolved-swap-id") {
                label shouldContain "swap_id"
                hint shouldNotContain "Did you mean"
                hint shouldContain "Available swaps"
            }
        }
    }

    test("unresolved swap id with suggestion") {
        val params = vars {
            swaps["swap_id"] = "..."
        }
        "//$ swap_if".shouldHaveProblems(params) {
            shouldHave(SYNTAX_GROUP + "unresolved-swap-id") {
                label shouldContain "swap_if"
                hint shouldContain "Did you mean"
                hint shouldContain "Available swaps"
            }
        }
    }
}

context(scope: Test.ExecutionScope)
private inline fun @receiver:Language("kotlin") String.shouldHaveProblems(parameters: ProcessParameters, action: List<ProblemReport>.() -> Unit) {
    val problems = mutableListOf<ProblemReport>()
    val scope = kotlin.runCatching { scope.parseSnippet(this, problems::plusAssign) }
        .getOrElse { return action(problems) }
    scope.accept(ExpressionChecker, ExpressionChecker.Context(parameters))
    action(problems)
}
