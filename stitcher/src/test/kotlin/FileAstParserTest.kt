@file:Suppress("PublicApiImplicitType", "NOTHING_TO_INLINE")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.document.issue.LAYOUT_GROUP
import dev.kikugie.stitcher.document.issue.ProblemReport
import dev.kikugie.stitcher.token.DefinitionToken.*
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeBlank
import io.kotest.matchers.string.shouldEndWith
import matcher.IMMEDIATE_THROW
import matcher.RootMatcher
import matcher.ast
import matcher.parseSnippet
import org.intellij.lang.annotations.Language
import util.shouldHave
import util.shouldNotHave

val FileAstParserTest by testSuite {
    testSuite("conditions") {
        test("closed scope") {
            """
                //? if true {
                println("OK!")
                //?}
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.SCOPED_OPENER }

                    content()
                }
                code<ConditionCloser>()
            }
        }

        test("line scope ending after one line") {
            """
                //? if true
                println("OK!")
                // println(null)
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LINE_OPENER }

                    content()
                }
                content { leaf.text shouldBe "\n" }
                comment()
            }
        }

        test("line scope skipping empty lines") {
            """
                //? if true
                // 
                
                println("OK!")
                // println(null)
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LINE_OPENER }

                    comment()
                    content()
                }
                content { leaf.text shouldBe "\n" }
                comment()
            }
        }

        test("word lookup scope") {
            """
                //? if true >>
                println("OK!"); println("OK!")
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LOOKUP_OPENER }

                    content { leaf.text shouldEndWith ";" }
                }
                content()
            }
        }

        test("string lookup scope") {
            """
                //? if true >> '('
                println("OK!")
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LOOKUP_OPENER }

                    content { leaf.text shouldBe "println" }
                }
                content()
            }
        }

        test("string capturing lookup scope") {
            """
                //? if true >>+ ';'
                println("OK!");println("OK!")
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LOOKUP_OPENER }

                    content { leaf.text shouldEndWith ";" }
                }
                content()
            }
        }

        test("closed else-chain") {
            """
                //? if true {
                println("ON")
                //?} else {
                //println("OFF")
                //?}
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.SCOPED_OPENER }

                    content()
                }
                code<ConditionExtension> {
                    check { definition.kind shouldBe Kind.SCOPED_EXTENSION }

                    comment()
                }
                code<ConditionCloser>()
            }
        }

        test("closed -> line else-chain") {
            """
                //? if true {
                println("ON")
                //?} else
                //println("OFF")
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.SCOPED_OPENER }

                    content()
                }
                code<ConditionExtension> {
                    check { definition.kind shouldBe Kind.LINE_EXTENSION }

                    comment()
                }
            }
        }

        test("line -> line else-chain") {
            """
                //? if true
                println("ON")
                //? else
                //println("OFF")
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LINE_OPENER }

                    content()
                    content { leaf.text shouldBe "\n" }
                }
                code<ConditionExtension> {
                    check { definition.kind shouldBe Kind.LINE_EXTENSION }

                    comment()
                }
            }
        }

        test("lookup -> line else-chain") {
            """
                //? if true >>+ ')'
                println("ON")
                //? else
                //println("OFF")
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LOOKUP_OPENER }

                    content()
                    content { leaf.text shouldBe "\n" }
                }
                code<ConditionExtension> {
                    check { definition.kind shouldBe Kind.LINE_EXTENSION }

                    comment()
                }
            }
        }

        test("nested closed -> closed") {
            """
                //? if true {
                    //? if true {
                    println("ON")
                    //?}
                //?}
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.SCOPED_OPENER }

                    content { leaf.text.shouldBeBlank() }
                    code<ConditionOpener> {
                        check { definition.kind shouldBe Kind.SCOPED_OPENER }

                        content()
                    }
                    code<ConditionCloser>()
                }
                code<ConditionCloser>()
            }
        }

        test("nested closed -> line") {
            """
                //? if true {
                    //? if true
                    println("ON")
                //?}
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.SCOPED_OPENER }

                    content { leaf.text.shouldBeBlank() }
                    code<ConditionOpener> {
                        check { definition.kind shouldBe Kind.LINE_OPENER }

                        content()
                    }
                    content { leaf.text.shouldBeBlank() }
                }
                code<ConditionCloser>()
            }
        }

        test("nested line -> line") {
            """
                //? if true
                    //? if true
                    println("ON")
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LINE_OPENER }

                    content { leaf.text.shouldBeBlank() }
                    code<ConditionOpener> {
                        check { definition.kind shouldBe Kind.LINE_OPENER }

                        content()
                    }
                }
            }
        }

        test("nested line -> scoped chain") {
            """
                //? if true
                    //? if true {
                    println("ON")
                    //?} else {
                    //println("OFF")
                    //?}
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LINE_OPENER }

                    content { leaf.text.shouldBeBlank() }
                    code<ConditionOpener> {
                        check { definition.kind shouldBe Kind.SCOPED_OPENER }

                        content()
                    }
                    code<ConditionExtension> {
                        check { definition.kind shouldBe Kind.SCOPED_EXTENSION }

                        content { leaf.text.shouldBeBlank() }
                        comment()
                        content { leaf.text.shouldBeBlank() }
                    }
                    code<ConditionCloser>()
                }
            }
        }

        test("nested line -> line chain") {
            """
                //? if true
                    //? if true
                    println("ON")

                    //? else
                    //println("OFF")
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LINE_OPENER }

                    content { leaf.text.shouldBeBlank() }
                    code<ConditionOpener> {
                        check { definition.kind shouldBe Kind.LINE_OPENER }

                        content()
                        content { leaf.text.shouldBeBlank() }
                    }
                    code<ConditionExtension> {
                        check { definition.kind shouldBe Kind.LINE_EXTENSION }

                        content { leaf.text.shouldBeBlank() }
                        comment()
                    }
                }
            }
        }

        test("interrupted line -> line nesting") {
            """
                //? if true
                    func()//? if true
                    println("ON")
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LINE_OPENER }

                    content()
                }
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LINE_OPENER }

                    content()
                }
            }
        }

        test("unfinished closed -> line nesting") {
            """
                //? if true {
                    //$ id
                    func()//?}
            """.trimIndent() shouldBeParsedAs ast {
                code<ConditionOpener> {
                    check { definition.kind shouldBe Kind.LINE_OPENER }

                    content { leaf.text.shouldBeBlank() }
                    code<SwapOpener> {
                        content()
                    }
                }
                code<ConditionCloser>()
            }
        }
    }

    testSuite("swaps") {
        test("closed scope") {
            """
                //$ id {
                println("OK!")
                // println("OK!")
                //$}
            """.trimIndent() shouldBeParsedAs ast {
                code<SwapOpener> {
                    content()
                    comment()
                }
                code<SwapCloser>()
            }
        }

        test("line scope") {
            """
                //$ id
                println("OK!")
                // println("OK!")
            """.trimIndent() shouldBeParsedAs ast {
                code<SwapOpener> {
                    content()
                }
                content { leaf.text shouldBe "\n" }
                comment()
            }
        }

        test("lookup scope") {
            """
                //$ id >> '('
                println("OK!")
            """.trimIndent() shouldBeParsedAs ast {
                code<SwapOpener> {
                    content()
                }
                content()
            }
        }

        test("closed scope ignore nested statements") {
            """
                //$ id {
                    //? if true {
                    //println("OK!")
                    //?}
                //$}
            """.trimIndent() shouldBeParsedAs ast {
                code<SwapOpener> {
                    content { leaf.text.shouldBeBlank() }
                    code<ConditionOpener>()
                    content { leaf.text.shouldBeBlank() }
                    comment()
                    content { leaf.text.shouldBeBlank() }
                    code<ConditionCloser>()
                }
                code<SwapCloser>()
            }
        }
    }

    testSuite("reported cases") {
        test("root closer ignoring other types") {
            """
                //$ id {
                func()
                //$}
                //?}
            """.trimIndent() shouldHaveProblems {
                shouldHave(LAYOUT_GROUP + "unmatched-root-closer") {
                    hint.shouldBeNull()
                }
            }
        }

        test("root closer with a candidate") {
            """
                //? if true
                func()
                //?}
            """.trimIndent() shouldHaveProblems {
                shouldHave(LAYOUT_GROUP + "unmatched-root-closer") {
                    hint.shouldNotBeNull()
                }
            }
        }

        test("root closer with a nested candidate") {
            """
                //? if true {
                //? if true
                func()
                //?}
                //?}
            """.trimIndent() shouldHaveProblems {
                shouldHave(LAYOUT_GROUP + "unmatched-root-closer") {
                    hint.shouldNotBeNull()
                }
            }
        }

        test("missing closing bracket") {
            """
                //? if true {
                func()
            """.trimIndent() shouldHaveProblems {
                shouldHave(LAYOUT_GROUP + "missing-scope-closer") {
                    hint.shouldNotBeNull()
                }
            }
        }

        test("missing closing bracket in swap scope") {
            """
                //$ id {
                //? if true {
                func()
                //$}
            """.trimIndent() shouldHaveProblems {
                shouldNotBeEmpty()
                shouldNotHave(LAYOUT_GROUP + "missing-scope-closer")
            }
        }

        test("code builders in swap scope") {
            """
                //$ id {
                //? if true {
                func()
                //$}
            """.trimIndent() shouldHaveProblems {
                shouldHave(LAYOUT_GROUP + "definition-in-swap-scope") {
                    severity shouldBe ProblemReport.Severity.WARN
                }
            }
        }

        test("invalid closer type") {
            """
                //? if true {
                //~}
            """.trimIndent() shouldHaveProblems {
                shouldHave(LAYOUT_GROUP + "mismatched-closer-type")
            }
        }

        test("invalid closer type ignored for open scope") {
            """
                //? if true
                func()//~}
            """.trimIndent() shouldHaveProblems {
                shouldNotBeEmpty()
                shouldNotHave(LAYOUT_GROUP + "mismatched-closer-type")
            }
        }

        test("invalid closer type ignored for swap scope") {
            """
                //$ id {
                //?}
                func()
                //$}
            """.trimIndent() shouldHaveProblems {
                shouldNotBeEmpty()
                shouldNotHave(LAYOUT_GROUP + "mismatched-closer-type")
            }
        }

        test("string lookup not found") {
            """
                //? if true >> 'A'
                func()
            """.trimIndent() shouldHaveProblems {
                shouldHave(LAYOUT_GROUP + "lookup-not-found") {
                    hint.shouldBeNull()
                }
            }
        }

        test("string lookup interrupted") {
            """
                //? if true >> 'A'
                func()
                //? if true
                A()
            """.trimIndent() shouldHaveProblems {
                shouldHave(LAYOUT_GROUP + "lookup-not-found") {
                    hint.shouldNotBeNull()
                }
            }
        }

        test("string lookup interrupted by a closer") {
            """
                //? if true {
                //? if true >> 'A'
                func()
                //?}
                A()
            """.trimIndent() shouldHaveProblems {
                shouldHave(LAYOUT_GROUP + "lookup-not-found") {
                    hint.shouldNotBeNull()
                }
            }
        }

        test("word lookup ignored") {
            """
                //? if true >>
                func()//? if true
                A()
            """.trimIndent() shouldHaveProblems {
                shouldNotHave(LAYOUT_GROUP + "lookup-not-found")
            }
        }
    }
}

context(scope: Test.ExecutionScope)
private inline infix fun @receiver:Language("kotlin") String.shouldBeParsedAs(matcher: RootMatcher) {
    val root = scope.parseSnippet(this, IMMEDIATE_THROW)
    matcher.test(root)
}

context(scope: Test.ExecutionScope)
private inline infix fun @receiver:Language("kotlin") String.shouldHaveProblems(action: List<ProblemReport>.() -> Unit) {
    val problems = mutableListOf<ProblemReport>()
    kotlin.runCatching { scope.parseSnippet(this, problems::plusAssign) }
    action(problems)
}
