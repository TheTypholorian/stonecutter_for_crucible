@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.token.type.RootScopeState
import io.kotest.matchers.shouldBe
import matcher.Commenter
import matcher.Scanner
import matcher.StitcherParametersBuilder
import matcher.Uncommenter
import matcher.assemble
import matcher.params

val ProcessorStabilityTest by testSuite {
    testSuite("persistent") {
        for ((family, build) in CONFIGS) for ((name, snippet) in CASES.getValue(family))
            test("$family: $name") {
                for (enabled in listOf(true, false)) {
                    val once = assemble(snippet, build(enabled, RootScopeState.ANY))
                    assemble(once, build(enabled, RootScopeState.ANY)) shouldBe once
                }
            }
    }

    testSuite("reversible") {
        for ((family, build) in CONFIGS) for ((name, snippet) in CASES.getValue(family))
            test("$family: $name") {
                val disabled = assemble(snippet, build(false, RootScopeState.ANY))
                val enabled = assemble(disabled, build(true, RootScopeState.ANY))
                assemble(enabled, build(false, RootScopeState.ANY)) shouldBe disabled
            }
    }

    testSuite("persistent root") {
        for ((family, build) in CONFIGS) for ((name, snippet) in CASES.getValue(family))
            test("$family: $name") {
                for (enabled in listOf(true, false)) for (root in listOf(RootScopeState.ENABLED, RootScopeState.DISABLED)) {
                    val once = assemble(snippet, build(enabled, root))
                    assemble(once, build(enabled, root)) shouldBe once
                }
            }
    }

    testSuite("reversible root") {
        for ((family, build) in CONFIGS) for ((name, snippet) in CASES.getValue(family))
            test("$family: $name") {
                for (enabled in listOf(true, false)) for (restored in listOf(RootScopeState.ENABLED, RootScopeState.ANY)) {
                    val expected = assemble(snippet, build(enabled, RootScopeState.ANY))
                    val disabled = assemble(snippet, build(enabled, RootScopeState.DISABLED))
                    assemble(disabled, build(enabled, restored)) shouldBe expected
                }
            }
    }
}

private typealias Build = (Boolean, RootScopeState) -> StitcherParameters

private val CONFIGS: Map<String, Build> = mapOf(
    "java" to { flag, root -> flags(flag, root) },
    "kotlin" to { flag, root -> flags(flag, root) { commenter = Commenter.Kotlin; scanner = Scanner.Kotlin } },
    "hash" to { flag, root -> flags(flag, root) { scanner = Scanner.Hash; commenter = Commenter.Hash; uncommenter = Uncommenter.Lines } },
    "xml" to { flag, root -> flags(flag, root) { scanner = Scanner.Xml; commenter = Commenter.Xml; uncommenter = Uncommenter.Xml } },
)

private val CASES: Map<String, Map<String, String>> = mapOf(
    "java" to mapOf(
        "scoped" to "//? if flag {\nval one = 1\nval two = 2\n//?}",
        "single line in scope" to "//? if flag {\nval one = 1\n//?}",
        "line scope" to "//? if flag\nval one = 1\nval two = 2",
        "indented" to "class A {\n    //? if flag {\n    val one = 1\n    val two = 2\n    //?}\n}",
        "else branch" to "//? if flag {\nval one = 1\n//?} else {\nval one = 2\n//?}",
        "nested" to "//? if flag {\nval outer = 0\n//? if flag {\nval inner = 1\n//?}\n//?}",
        "trailing content" to "//? if flag {\nval one = 1\n//?}\nval after = 2",
        "blank lines inside" to "//? if flag {\nval one = 1\n\nval two = 2\n//?}",
    ),
    "kotlin" to mapOf(
        "scoped" to "//? if flag {\nval one = 1\nval two = 2\n//?}",
        "nested" to "//? if flag {\nval outer = 0\n//? if flag {\nval inner = 1\n//?}\n//?}",
    ),
    "hash" to mapOf(
        "scoped" to "#? if flag {\nkey = 1\nother = 2\n#?}",
        "line scope" to "#? if flag\nkey = 1\nother = 2",
        "indented" to "root:\n  #? if flag {\n  a: 1\n  b: 2\n  #?}",
        "else branch" to "#? if flag {\nkey = 1\n#?} else {\nkey = 2\n#?}",
        "trailing content" to "#? if flag {\nkey = 1\n#?}\nafter = 2",
    ),
    "xml" to mapOf(
        "scoped" to "<!--? if flag {-->\n<item id=\"a\"/>\n<item id=\"b\"/>\n<!--?}-->",
        "line scope" to "<!--? if flag-->\n<item id=\"a\"/>\n<item id=\"b\"/>",
        "indented" to "<root>\n  <!--? if flag {-->\n  <item id=\"a\"/>\n  <!--?}-->\n</root>",
        "nested" to "<!--? if flag {-->\n<a/>\n<!--? if flag {-->\n<b/>\n<!--?}-->\n<!--?}-->",
        "trailing content" to "<!--? if flag {-->\n<item id=\"a\"/>\n<!--?}-->\n<tail/>",
    ),
)

private fun flags(flag: Boolean, root: RootScopeState, action: StitcherParametersBuilder.() -> Unit = {}): StitcherParameters = params {
    constants["flag"] = flag
    this.root = root
    action()
}
