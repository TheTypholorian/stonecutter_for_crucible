@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.stitcher.StitcherParameters
import io.kotest.matchers.shouldBe
import matcher.Commenter
import matcher.Scanner
import matcher.StitcherParametersBuilder
import matcher.Uncommenter
import matcher.assemble
import matcher.params
import org.intellij.lang.annotations.Language

val HashHandlingTest by testSuite {
    testSuite("conditions") {
        test("disable scoped") {
            """
                #? if false {
                key = 1
                other = 2
                #?}
            """.trimIndent() shouldAssembleTo """
                #? if false {
                #key = 1
                #other = 2
                #?}
            """.trimIndent()
        }

        test("enable scoped") {
            """
                #? if true {
                #key = 1
                #other = 2
                #?}
            """.trimIndent() shouldAssembleTo """
                #? if true {
                key = 1
                other = 2
                #?}
            """.trimIndent()
        }

        test("disable line") {
            """
                #? if false
                key = 1
                other = 2
            """.trimIndent() shouldAssembleTo """
                #? if false
                #key = 1
                other = 2
            """.trimIndent()
        }

        test("enable line") {
            """
                #? if true
                #key = 1
                other = 2
            """.trimIndent() shouldAssembleTo """
                #? if true
                key = 1
                other = 2
            """.trimIndent()
        }

        test("pick the branch matching its condition") {
            """
                #? if false
                key = 1
                #? else
                #key = 2
            """.trimIndent() shouldAssembleTo """
                #? if false
                #key = 1
                #? else
                key = 2
            """.trimIndent()
        }
    }

    testSuite("indentation") {
        // The comment marker goes after the common indent, so nesting-sensitive formats stay valid.
        test("keep the common indent of a scope") {
            """
                root:
                  #? if false {
                  a: 1
                  b: 2
                  #?}
            """.trimIndent() shouldAssembleTo """
                root:
                  #? if false {
                  #a: 1
                  #b: 2
                  #?}
            """.trimIndent()
        }

        test("restore the common indent on enable") {
            """
                root:
                  #? if true {
                  #a: 1
                  #b: 2
                  #?}
            """.trimIndent() shouldAssembleTo """
                root:
                  #? if true {
                  a: 1
                  b: 2
                  #?}
            """.trimIndent()
        }
    }

    testSuite("string modes") {
        // `#` inside a double-quoted value is not a comment, so the real marker below is still found.
        test("hash inside a quoted value is not a marker") {
            """
                name = "value # not a comment"
                #? if false
                key = 1
            """.trimIndent() shouldAssembleTo """
                name = "value # not a comment"
                #? if false
                #key = 1
            """.trimIndent()
        }

        test("apostrophe inside a quoted value is harmless") {
            """
                desc = "don't panic"
                #? if false
                key = 1
            """.trimIndent() shouldAssembleTo """
                desc = "don't panic"
                #? if false
                #key = 1
            """.trimIndent()
        }

        test("marker inside a quoted value is inert") {
            """
                pattern = "#? if true"
                #? if false
                key = 1
            """.trimIndent() shouldAssembleTo """
                pattern = "#? if true"
                #? if false
                #key = 1
            """.trimIndent()
        }
    }

    testSuite("swaps and replacements") {
        test("swap substitutes the scope") {
            """
                #$ version {
                old
                #$}
            """.trimIndent().shouldAssembleTo(
                hash { swaps["version"] = "1.21.1" }, """
                #$ version {
                1.21.1
                #$}
            """.trimIndent()
            )
        }

        test("replacement applies to plain values") {
            "key = old".shouldAssembleTo(hash { string("old", "new") }, "key = new")
        }

        test("targets resolve in a hash-comment file") {
            """
                #? if >=1.21 {
                feature = true
                #?}
            """.trimIndent().shouldAssembleTo(
                hash { targets[""] = SemanticVersion(1, 20, 1) }, """
                #? if >=1.21 {
                #feature = true
                #?}
            """.trimIndent()
            )
        }
    }

    testSuite("real documents") {
        test("neoforge.mods.toml") {
            """
                modLoader = "javafml"
                #? if >=1.21 {
                loaderVersion = "[4,)"
                #?} else
                #loaderVersion = "[2,)"
                license = "MIT"
            """.trimIndent().shouldAssembleTo(
                hash { targets[""] = SemanticVersion(1, 20, 1) }, """
                modLoader = "javafml"
                #? if >=1.21 {
                #loaderVersion = "[4,)"
                #?} else
                loaderVersion = "[2,)"
                license = "MIT"
            """.trimIndent()
            )
        }

        test("access widener") {
            """
                accessWidener v2 named
                #? if >=1.21
                accessible method net/minecraft/Example method_1 ()V
            """.trimIndent().shouldAssembleTo(
                hash { targets[""] = SemanticVersion(1, 21, 4) }, """
                accessWidener v2 named
                #? if >=1.21
                accessible method net/minecraft/Example method_1 ()V
            """.trimIndent()
            )
        }
    }
}

private fun hash(action: StitcherParametersBuilder.() -> Unit = {}): StitcherParameters = params {
    scanner = Scanner.Hash
    commenter = Commenter.Hash
    uncommenter = Uncommenter.Lines
    action()
}

context(scope: Test.ExecutionScope)
private infix fun @receiver:Language("yaml") String.shouldAssembleTo(@Language("yaml") expected: String): Unit =
    shouldAssembleTo(hash(), expected)

context(scope: Test.ExecutionScope)
private fun @receiver:Language("yaml") String.shouldAssembleTo(params: StitcherParameters, @Language("yaml") expected: String) {
    scope.assemble(this, params) shouldBe expected
}
