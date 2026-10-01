@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.antlr.JavaStyleScanner
import dev.kikugie.stitcher.antlr.KotlinStyleScanner
import dev.kikugie.stitcher.antlr.charStream
import io.kotest.matchers.shouldBe
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Token

private const val NESTED = "/* a /* b */ c */ d"

private fun Lexer.tokens(): List<Pair<Int, Int>> = allTokens
    .filter { it.type != Token.EOF }
    .map { it.type to it.startIndex }

val SlashScannerTest by testSuite {
    test("java closes block comment at first end") {
        JavaStyleScanner(NESTED.charStream()).tokens() shouldBe listOf(
            JavaStyleScanner.Tokens.STAR_COMMENT_START to 0,
            JavaStyleScanner.Tokens.STAR_COMMENT_END to 10,
        )
    }

    test("kotlin closes block comment at matching end") {
        KotlinStyleScanner(NESTED.charStream()).tokens() shouldBe listOf(
            KotlinStyleScanner.Tokens.STAR_COMMENT_START to 0,
            KotlinStyleScanner.Tokens.STAR_COMMENT_END to 15,
        )
    }
}
