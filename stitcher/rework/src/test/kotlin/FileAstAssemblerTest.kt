@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.StitcherParameters
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import matcher.assemble
import matcher.params
import org.intellij.lang.annotations.Language

val FileAstAssemblerTest by testSuite {
    testSuite("conditions") {
        test("active branch stays, inactive stays commented") {
            """
                //? if true {
                println("ON")
                //?} else {
                //println("OFF")
                //?}
            """.trimIndent().shouldAssembleTo(
                """
                    //? if true {
                    println("ON")
                    //?} else {
                    //println("OFF")
                    //?}
                """.trimIndent()
            )
        }

        test("flipping the condition swaps which branch is active") {
            """
                //? if false {
                println("ON")
                //?} else {
                //println("OFF")
                //?}
            """.trimIndent().shouldAssembleTo(
                """
                    //? if false {
                    //println("ON")
                    //?} else {
                    println("OFF")
                    //?}
                """.trimIndent()
            )
        }

        test("if / elif / else keeps the first true branch active") {
            val params = params { constants["a"] = false; constants["b"] = true }
            """
                //? if a {
                //A
                //?} elif b {
                B
                //?} else {
                //C
                //?}
            """.trimIndent().shouldAssembleTo(
                """
                    //? if a {
                    //A
                    //?} elif b {
                    B
                    //?} else {
                    //C
                    //?}
                """.trimIndent(),
                params
            )
        }

        test("a nested block comment is flattened when its branch is disabled") {
            """
                //? if false {
                a
                /* note */
                b
                //?}
            """.trimIndent().let { input ->
                assemble(input) shouldContain "/^ note ^/"
            }
        }
    }

    testSuite("replacements") {
        test("unconditional regex replacement applies to active content") {
            """
                //? if true {
                val x = OLD
                //?}
            """.trimIndent().shouldAssembleTo(
                """
                    //? if true {
                    val x = NEW
                    //?}
                """.trimIndent(),
                params { regex("OLD", "NEW") }
            )
        }

        test("toggle enables an id-gated replacement for following content") {
            """
                //~ swap
                val x = OLD
            """.trimIndent().shouldAssembleTo(
                """
                    //~ swap
                    val x = NEW
                """.trimIndent(),
                params { regex("OLD", "NEW", "swap") }
            )
        }
    }

    testSuite("indentation") {
        test("swap substitution is re-indented to the scope") {
            """
                object X {
                    //$ body {
                    old
                    //$}
                }
            """.trimIndent().shouldAssembleTo(
                """
                    object X {
                        //$ body {
                        line1
                        line2
                        //$}
                    }
                """.trimIndent(),
                params { swaps["body"] = "line1\nline2" }
            )
        }

        test("mid-line swap leaves the first line un-indented") {
            """
                object X {
                    /*$ pair {*/old
                    more
                    //$}
                }
            """.trimIndent().shouldAssembleTo(
                """
                    object X {
                        /*$ pair {*/P
                        Q
                        //$}
                    }
                """.trimIndent(),
                params { swaps["pair"] = "P\nQ" }
            )
        }
    }

    testSuite("stability") {
        test("assembling is idempotent") {
            val input = """
                //? if false {
                println("ON")
                //?} else {
                //println("OFF")
                //?}
            """.trimIndent()
            val once = assemble(input)
            assemble(once) shouldBe once
        }
    }

    testSuite("swaps") {
        test("named swap substitutes its scope") {
            """
                //$ id {
                old
                //$}
            """.trimIndent().shouldAssembleTo(
                """
                    //$ id {
                    new
                    //$}
                """.trimIndent(),
                params { swaps["id"] = "new" }
            )
        }

        test("template swap fills numbered placeholders from arguments") {
            """
                //$ greet 'a' 'b' {
                old
                //$}
            """.trimIndent().shouldAssembleTo(
                """
                    //$ greet 'a' 'b' {
                    a-b
                    //$}
                """.trimIndent(),
                params { swaps["greet"] = "\$1-\$2" }
            )
        }
    }
}

context(scope: Test.ExecutionScope)
private fun String.shouldAssembleTo(expected: String, params: StitcherParameters = params()) {
    scope.assemble(this, params) shouldBe expected
}
