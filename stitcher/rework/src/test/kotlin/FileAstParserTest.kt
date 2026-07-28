@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.antlr.StitcherErrorListener
import dev.kikugie.stitcher.antlr.errorListener
import dev.kikugie.stitcher.document.SourceDocument
import dev.kikugie.stitcher.document.issue.runThrowing
import dev.kikugie.stitcher.parser.FileAstParser
import dev.kikugie.stitcher.parser.FileTokenStream
import dev.kikugie.stitcher.token.DefinitionToken
import matcher.IMMEDIATE_THROW
import matcher.RootMatcher
import matcher.Scanner
import matcher.ast
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.UnbufferedTokenStream
import kotlin.io.path.Path

val FileAstParserTest by testSuite {
    testSuite("conditions") {
        test("line scope") {
            """
                // if true
                foo bar
            """.trimIndent() shouldBeParsedAs ast {
                content()
                code<DefinitionToken.ConditionOpener> {
                    content()
                }
            }
        }
    }
}

context(scope: Test.ExecutionScope)
private infix fun String.shouldBeParsedAs(matcher: RootMatcher) {
    val document = SourceDocument(Path("${scope.testElementPath}.txt"), this, IMMEDIATE_THROW)
    val source = Scanner.Java.create(CharStreams.fromString(this), document).apply {
        scanner.errorListener = StitcherErrorListener(document)
    }

    val root = document.runThrowing {
        val stream = FileTokenStream(document, UnbufferedTokenStream<Token>(source))
        FileAstParser.parse(stream)
    }

    matcher.test(root)
}
