@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.DefinitionToken.*
import dev.kikugie.stitcher.transformer.FileAstUncommenter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeBlank
import io.kotest.matchers.string.shouldContain
import matcher.IMMEDIATE_THROW
import matcher.RootMatcher
import matcher.ast
import matcher.params
import matcher.parseSnippet
import org.intellij.lang.annotations.Language

val FileAstUncommenterTest by testSuite {
    test("line comments") {
        """
            //? if true {
            //...
            //...
            //?}
        """.trimIndent().shouldBeParsedAs {
            code<ConditionOpener> {
                content {
                    leaf.text shouldBe "...\n...\n"
                }
            }
            code<ConditionCloser>()
        }
    }

    test("nested line comment") {
        """
            //? if true {
            // /^...^/
            //?}
        """.trimIndent().shouldBeParsedAs {
            code<ConditionOpener> {
                content { leaf.text shouldContain "/^...^/" }
            }
            code<ConditionCloser>()
        }
    }

    test("single multiline comment") {
        """
            //? if true {
            /*...
            ...
            *///?}
        """.trimIndent().shouldBeParsedAs {
            code<ConditionOpener> {
                content {
                    leaf.text shouldBe "...\n...\n"
                }
            }
            code<ConditionCloser>()
        }
    }

    test("nested multiline comment") {
        """
            //? if true {
            /*
            /^...^/
            *///?}
        """.trimIndent().shouldBeParsedAs {
            code<ConditionOpener> {
                content { leaf.text.shouldBeBlank() }
                comment { opener?.text shouldBe "/*" }
                content { leaf.text.shouldBeBlank() }
            }
            code<ConditionCloser>()
        }
    }

    test("double nested multiline comment") {
        """
            //? if true {
            /*
            /^ /^...^/ ^/
            *///?}
        """.trimIndent().shouldBeParsedAs {
            code<ConditionOpener> {
                content { leaf.text.shouldBeBlank() }
                comment {
                    opener?.text shouldBe "/*"
                    body.text shouldBe " /^...^/ "
                }
                content { leaf.text.shouldBeBlank() }
            }
            code<ConditionCloser>()
        }
    }

    test("ignore nested markers in single quotes") {
        """
            //? if true {
            /*'/^' '^/'
            *///?}
        """.trimIndent().shouldBeParsedAs {
            code<ConditionOpener> {
                content { leaf.text shouldContain "'/^' '^/'" }
            }
            code<ConditionCloser>()
        }
    }

    test("ignore nested markers in double quotes") {
        """
            //? if true {
            /*"/^" "^/"
            *///?}
        """.trimIndent().shouldBeParsedAs {
            code<ConditionOpener> {
                content { leaf.text shouldContain "\"/^\" \"^/\"" }
            }
            code<ConditionCloser>()
        }
    }

    test("ignore nested markers in slash comments") {
        """
            //? if true {
            /* // /^...^/
            *///?}
        """.trimIndent().shouldBeParsedAs {
            code<ConditionOpener> {
                content { leaf.text.shouldBeBlank() }
                // The `//` line comment is preserved verbatim; its `\n` closer consumes the trailing newline.
                comment { body.text shouldContain "/^...^/" }
            }
            code<ConditionCloser>()
        }
    }
}

context(scope: Test.ExecutionScope)
private inline fun @receiver:Language("kotlin") String.shouldBeParsedAs(
    params: StitcherParameters = params(), builder: RootMatcher.() -> Unit
) {
    val root = scope.parseSnippet(this, IMMEDIATE_THROW)
        .accept(FileAstUncommenter(params), null) as BlockToken.Root
    ast(builder).test(root)
}
