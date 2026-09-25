@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.StitcherParameters
import io.kotest.matchers.shouldBe
import matcher.Commenter
import matcher.Scanner
import matcher.StitcherParametersBuilder
import matcher.Uncommenter
import matcher.assemble
import matcher.params
import org.intellij.lang.annotations.Language

val XmlHandlingTest by testSuite {
    testSuite("conditions") {
        test("disable scoped") {
            """
                <!--? if false {-->
                <item id="a"/>
                <!--?}-->
            """.trimIndent() shouldAssembleTo """
                <!--? if false {-->
                <!--<item id="a"/>
                --><!--?}-->
            """.trimIndent()
        }

        test("enable scoped") {
            """
                <!--? if true {-->
                <!--<item id="a"/>
                --><!--?}-->
            """.trimIndent() shouldAssembleTo """
                <!--? if true {-->
                <item id="a"/>
                <!--?}-->
            """.trimIndent()
        }

        test("disable single line") {
            """
                <!--? if false-->
                <item id="a"/>
            """.trimIndent() shouldAssembleTo """
                <!--? if false-->
                <!--<item id="a"/>-->
            """.trimIndent()
        }
    }

    testSuite("nested comments") {
        // XML forbids `--` inside a comment, so an enclosed marker is escaped to `<!^^`/`^^>`,
        // leaving the outer comment well-formed.
        test("escape an enclosed scope") {
            """
                <!--? if false {-->
                <!--? if false {-->
                <item id="a"/>
                <!--?}-->
                <!--?}-->
            """.trimIndent() shouldAssembleTo """
                <!--? if false {-->
                <!--<!^^? if false {^^>
                <!^^<item id="a"/>
                ^^><!^^?}^^>
                --><!--?}-->
            """.trimIndent()
        }

        test("restore an escaped comment") {
            """
                <!--? if true {-->
                <!--<!^^ note ^^>
                --><!--?}-->
            """.trimIndent() shouldAssembleTo """
                <!--? if true {-->
                <!-- note -->
                <!--?}-->
            """.trimIndent()
        }

        // Engine behaviour shared with the Java: an already-enabled comment enclosed by a
        // scope has its delimiters stripped rather than escaped, so the body is re-commented as text.
        test("strip an enclosed enabled comment") {
            """
                <!--? if false {-->
                <!-- note -->
                <!--?}-->
            """.trimIndent() shouldAssembleTo "<!--? if false {-->\n <!--note \n--><!--?}-->"
        }
    }

    testSuite("dashes") {
        for ((name, content, escaped) in listOf(
            Triple("double dash", "<arg>--enable-preview</arg>", "<arg>-^-enable-preview</arg>"),
            Triple("triple dash", "a---b", "a-^-^-b"),
            Triple("trailing dash", "value-", "value-^"),
            Triple("escaped dash", "a-^b", "a-^^b"),
        )) {
            test("escape $name") {
                "<!--? if false-->\n$content" shouldAssembleTo "<!--? if false-->\n<!--$escaped-->"
            }

            test("restore $name") {
                "<!--? if true-->\n<!--$escaped-->" shouldAssembleTo "<!--? if true-->\n$content"
            }
        }

        test("round-trip a dash before an enclosed closer") {
            val nested = "<!--? if false {-->\n<!--? if false-->\nx-\n<!--?}-->"
            val disabled = assemble(nested, xml())
            disabled shouldBe "<!--? if false {-->\n<!--<!^^? if false^^>\n<!^^x-^^^^>\n--><!--?}-->"

            val enabled = disabled.replace("<!--? if false {-->", "<!--? if true {-->")
            assemble(enabled, xml()) shouldBe "<!--? if true {-->\n<!--? if false-->\n<!--x-^-->\n<!--?}-->"
        }

        test("restore a dash before an enclosed closer") {
            """
                <!--? if true {-->
                <!--<!^^ a-^^^>
                --><!--?}-->
            """.trimIndent() shouldAssembleTo """
                <!--? if true {-->
                <!-- a--->
                <!--?}-->
            """.trimIndent()
        }

        test("restore an enclosed closer escaped without dash escaping") {
            """
                <!--? if true {-->
                <!--<!^^ a-^^>
                --><!--?}-->
            """.trimIndent() shouldAssembleTo """
                <!--? if true {-->
                <!-- a--->
                <!--?}-->
            """.trimIndent()
        }
    }

    testSuite("swaps and replacements") {
        test("swap substitutes the scope") {
            "<!--$ id {-->old<!--$}-->"
                .shouldAssembleTo(xml { swaps["id"] = "new" }, "<!--$ id {-->new<!--$}-->")
        }

        test("replacement applies to markup text") {
            "<item id=\"old\"/>"
                .shouldAssembleTo(xml { string("old", "new") }, "<item id=\"new\"/>")
        }

        // An apostrophe in character data must not open a string mode the scanner never leaves.
        test("apostrophe in text does not hide a later marker") {
            """
                <p>don't panic</p>
                <!--? if false-->
                <item id="a"/>
            """.trimIndent() shouldAssembleTo """
                <p>don't panic</p>
                <!--? if false-->
                <!--<item id="a"/>-->
            """.trimIndent()
        }
    }
}

private fun xml(action: StitcherParametersBuilder.() -> Unit = {}): StitcherParameters = params {
    scanner = Scanner.Xml
    commenter = Commenter.Xml
    uncommenter = Uncommenter.Xml
    action()
}

context(scope: Test.ExecutionScope)
private infix fun @receiver:Language("XML") String.shouldAssembleTo(@Language("XML") expected: String): Unit =
    shouldAssembleTo(xml(), expected)

context(scope: Test.ExecutionScope)
private fun @receiver:Language("XML") String.shouldAssembleTo(params: StitcherParameters, @Language("XML") expected: String) {
    scope.assemble(this, params) shouldBe expected
}
