@file:Suppress("PublicApiImplicitType", "NOTHING_TO_INLINE")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.antlr.errorListener
import dev.kikugie.stitcher.document.SourceDocument
import dev.kikugie.stitcher.document.issue.ProblemReport
import dev.kikugie.stitcher.document.issue.output.ProblemConsumer
import dev.kikugie.stitcher.document.issue.runThrowing
import dev.kikugie.stitcher.parser.FileAstParser
import dev.kikugie.stitcher.parser.FileTokenStream
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.DefinitionToken
import dev.kikugie.stitcher.token.DefinitionToken.ConditionCloser
import dev.kikugie.stitcher.token.DefinitionToken.ConditionExtension
import dev.kikugie.stitcher.token.DefinitionToken.ConditionOpener
import dev.kikugie.stitcher.token.DefinitionToken.Kind
import dev.kikugie.stitcher.token.DefinitionToken.SwapCloser
import dev.kikugie.stitcher.token.DefinitionToken.SwapOpener
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeBlank
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldEndWith
import matcher.IMMEDIATE_THROW
import matcher.RootMatcher
import matcher.Scanner
import matcher.ast
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.UnbufferedTokenStream
import org.intellij.lang.annotations.Language
import kotlin.io.path.Path

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
}

context(scope: Test.ExecutionScope)
private inline infix fun @receiver:Language("kotlin") String.shouldBeParsedAs(matcher: RootMatcher) {
    val root = parseSnippet(this, IMMEDIATE_THROW)
    matcher.test(root)
}

context(scope: Test.ExecutionScope)
private inline infix fun @receiver:Language("kotlin") String.shouldHaveProblems(action: List<ProblemReport>.() -> Unit) {
    val problems = mutableListOf<ProblemReport>()
    shouldThrowAny { parseSnippet(this, problems::plusAssign) }
    action(problems)
}


context(scope: Test.ExecutionScope)
private fun parseSnippet(value: String, consumer: ProblemConsumer): BlockToken.Root {
    val document = SourceDocument(Path("${scope.testElementPath}"), value, consumer)
    val source = Scanner.Java.create(CharStreams.fromString(value), document).apply {
        scanner.errorListener = StitcherErrorListener(document)
    }

    return document.runThrowing {
        val stream = FileTokenStream(document, UnbufferedTokenStream<Token>(source))
        FileAstParser.parse(stream)
    }
}
