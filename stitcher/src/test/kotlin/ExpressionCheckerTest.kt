@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.stitcher.document.issue.DefaultProblemReporter
import dev.kikugie.stitcher.document.issue.ProblemReport
import dev.kikugie.stitcher.document.issue.ProblemReport.Severity
import dev.kikugie.stitcher.document.issue.SYNTAX_GROUP
import dev.kikugie.stitcher.transformer.ExpressionChecker
import dev.kikugie.stonecutter.model.process.ProcessParameters
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import matcher.parseSnippet
import matcher.vars
import org.intellij.lang.annotations.Language
import util.shouldHave
import util.shouldNotHave

val ExpressionCheckerTest by testSuite {
    testSuite("swaps") {
        test("unresolved swap id") {
            "//$ swap_id".shouldHaveProblems {
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

        test("insufficient swap insertions") {
            val params = vars {
                swaps["my_swap"] = $$"$1 $2"
            }
            "//$ my_swap 'a'".shouldHaveProblems(params) {
                shouldHave(SYNTAX_GROUP + "insufficient-swap-insertions") {
                    label shouldContain "my_swap"
                    info shouldContain "2 parameters"
                    info shouldContain "1 were provided"
                }
            }
        }

        test("sufficient swap insertions") {
            val params = vars {
                swaps["my_swap"] = $$"$1 $2"
            }
            "//$ my_swap 'a' 'b'".shouldHaveProblems(params) {
                shouldNotHave(SYNTAX_GROUP + "insufficient-swap-insertions")
            }
        }

        test("swap insertions ignored without arguments") {
            val params = vars {
                swaps["my_swap"] = $$"$1 $2"
            }
            "//$ my_swap".shouldHaveProblems(params) {
                shouldNotHave(SYNTAX_GROUP + "unresolved-swap-id")
                shouldNotHave(SYNTAX_GROUP + "insufficient-swap-insertions")
            }
        }
    }

    testSuite("constants") {
        test("unresolved constant") {
            "//? if flag".shouldHaveProblems(vars { constants.clear() }) {
                shouldHave(SYNTAX_GROUP + "unresolved-constant-id") {
                    label shouldContain "flag"
                    hint shouldNotContain "Did you mean"
                    hint shouldNotContain "Available constants"
                }
            }
        }

        test("unresolved constant with suggestion") {
            val params = vars {
                constants["debug"] = true
            }
            "//? if debg".shouldHaveProblems(params) {
                shouldHave(SYNTAX_GROUP + "unresolved-constant-id") {
                    label shouldContain "debg"
                    hint shouldContain "Did you mean"
                    hint shouldContain "Available constants"
                }
            }
        }

        test("resolved constant") {
            "//? if true".shouldHaveProblems {
                shouldNotHave(SYNTAX_GROUP + "unresolved-constant-id")
            }
        }
    }

    testSuite("predicate targets") {
        test("unresolved explicit target") {
            "//? if forge: >1.0.0".shouldHaveProblems {
                shouldHave(SYNTAX_GROUP + "invalid-target-id") {
                    label shouldContain "forge"
                    hint shouldNotContain "Did you mean"
                    hint shouldNotContain "Available targets"
                }
            }
        }

        test("unresolved explicit target with suggestion") {
            val params = vars {
                targets["minecraft"] = SemanticVersion(1, 0, 0)
            }
            "//? if mincraft: >1.0.0".shouldHaveProblems(params) {
                shouldHave(SYNTAX_GROUP + "invalid-target-id") {
                    label shouldContain "mincraft"
                    hint shouldContain "Did you mean"
                    hint shouldContain "Available targets"
                }
            }
        }

        test("unresolved implicit target") {
            "//? if >1.0.0".shouldHaveProblems {
                shouldHave(SYNTAX_GROUP + "invalid-target-id") {
                    label shouldContain "default"
                    hint shouldContain "What have you done"
                }
            }
        }

        test("resolved explicit target") {
            val params = vars {
                targets["minecraft"] = SemanticVersion(1, 0, 0)
            }
            "//? if minecraft: >1.0.0".shouldHaveProblems(params) {
                shouldNotHave(SYNTAX_GROUP + "invalid-target-id")
            }
        }
    }

    testSuite("toggle replacements") {
        test("unresolved replacement id") {
            "//~ unknown".shouldHaveProblems {
                shouldHave(SYNTAX_GROUP + "unresolved-replacement-id") {
                    label shouldContain "unknown"
                    hint shouldNotContain "Did you mean"
                    hint shouldNotContain "Available replacements"
                }
            }
        }

        test("unresolved replacement id with listing") {
            val params = vars {
                string("a", "b", "legacy")
            }
            "//~ unknown".shouldHaveProblems(params) {
                shouldHave(SYNTAX_GROUP + "unresolved-replacement-id") {
                    label shouldContain "unknown"
                    hint shouldNotContain "Did you mean"
                    hint shouldContain "Available replacements"
                }
            }
        }

        test("unresolved replacement id with suggestion") {
            val params = vars {
                string("a", "b", "legacy")
            }
            "//~ legcy".shouldHaveProblems(params) {
                shouldHave(SYNTAX_GROUP + "unresolved-replacement-id") {
                    label shouldContain "legcy"
                    hint shouldContain "Did you mean"
                    hint shouldContain "Available replacements"
                }
            }
        }

        test("redundant enable of an already-active replacement") {
            val params = vars {
                string("a", "b", "feature", enabled = true)
            }
            "//~ feature".shouldHaveProblems(params) {
                shouldHave(SYNTAX_GROUP + "replacement-already-enabled") {
                    severity shouldBe Severity.INFO
                }
            }
        }

        test("redundant disable of an already-inactive replacement") {
            val params = vars {
                string("a", "b", "feature")
            }
            "//~ !feature".shouldHaveProblems(params) {
                shouldHave(SYNTAX_GROUP + "replacement-already-disabled") {
                    severity shouldBe Severity.INFO
                }
            }
        }

        test("enabling an inactive replacement is not redundant") {
            val params = vars {
                string("a", "b", "feature")
            }
            "//~ feature".shouldHaveProblems(params) {
                shouldNotHave(SYNTAX_GROUP + "replacement-already-enabled")
                shouldNotHave(SYNTAX_GROUP + "replacement-already-disabled")
            }
        }

        test("disabling an active replacement is not redundant") {
            val params = vars {
                string("a", "b", "feature", enabled = true)
            }
            "//~ !feature".shouldHaveProblems(params) {
                shouldNotHave(SYNTAX_GROUP + "replacement-already-enabled")
                shouldNotHave(SYNTAX_GROUP + "replacement-already-disabled")
            }
        }
    }

    testSuite("local replacements") {
        test("empty replacement source") {
            "//~ if true '' -> 'value'".shouldHaveProblems {
                shouldHave(SYNTAX_GROUP + "empty-replacement-value")
            }
        }

        test("empty replacement target") {
            "//~ if true 'value' -> ''".shouldHaveProblems {
                shouldHave(SYNTAX_GROUP + "empty-replacement-value")
            }
        }

        test("non-empty replacement values") {
            "//~ if true 'a' -> 'b'".shouldHaveProblems {
                shouldNotHave(SYNTAX_GROUP + "empty-replacement-value")
            }
        }
    }

    testSuite("condition chains") {
        test("else followed by another else") {
            """
                //? if true
                a
                //? else
                b
                //? else
                c
            """.trimIndent().shouldHaveProblems {
                shouldHave(SYNTAX_GROUP + "condition-after-else") {
                    severity shouldBe Severity.WARN
                }
            }
        }

        test("else followed by elif") {
            """
                //? if true
                a
                //? else
                b
                //? else if false
                c
            """.trimIndent().shouldHaveProblems {
                shouldHave(SYNTAX_GROUP + "condition-after-else") {
                    severity shouldBe Severity.WARN
                }
            }
        }

        test("closed else branch is fine") {
            """
                //? if true {
                a
                //?} else {
                //b
                //?}
            """.trimIndent().shouldHaveProblems {
                shouldNotHave(SYNTAX_GROUP + "condition-after-else")
            }
        }

        test("if / elif / else chain is fine") {
            """
                //? if true
                a
                //? else if false
                b
                //? else
                c
            """.trimIndent().shouldHaveProblems {
                shouldNotHave(SYNTAX_GROUP + "condition-after-else")
            }
        }

        test("separate chains do not leak state") {
            """
                //? if true
                a
                //? else
                b
                //? if false
                c
                //? else
                d
            """.trimIndent().shouldHaveProblems {
                shouldNotHave(SYNTAX_GROUP + "condition-after-else")
            }
        }
    }
}

context(scope: Test.ExecutionScope)
private inline fun @receiver:Language("kotlin") String.shouldHaveProblems(
    parameters: ProcessParameters = vars(),
    action: List<ProblemReport>.() -> Unit
) {
    val problems = mutableListOf<ProblemReport>()
    val reporter = DefaultProblemReporter(problems::plusAssign)
    val scope = kotlin.runCatching { scope.parseSnippet(this, reporter) }
        .getOrElse { return action(problems) }
    scope.accept(ExpressionChecker, ExpressionChecker.Context(parameters))
    action(problems)
}
