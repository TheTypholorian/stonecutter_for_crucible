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

    testSuite("swaps and replacements") {
        test("swap substitutes the scope") {
            "<!--\$ id {-->old<!--\$}-->"
                .shouldAssembleTo(xml { swaps["id"] = "new" }, "<!--\$ id {-->new<!--\$}-->")
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
