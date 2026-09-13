@file:Suppress("PublicApiImplicitType")

package unit

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stonecutter.util.escape
import dev.kikugie.stonecutter.util.unescape
import io.kotest.matchers.shouldBe

private const val FORM_FEED: Int = 0x0C

val StringEscapeTest by testSuite {
    testSuite("escape") {
        test("named sequences") {
            '\b'.escape() shouldBe "\\b"
            '\t'.escape() shouldBe "\\t"
            '\n'.escape() shouldBe "\\n"
            '\r'.escape() shouldBe "\\r"
            Char(FORM_FEED).escape() shouldBe "\\f"
            '"'.escape() shouldBe "\\\""
            '\\'.escape() shouldBe "\\\\"
        }

        test("control characters use four lowercase hex digits") {
            Char(0x00).escape() shouldBe "\\u0000"
            Char(0x0B).escape() shouldBe "\\u000b"
            Char(0x0F).escape() shouldBe "\\u000f"
            Char(0x10).escape() shouldBe "\\u0010"
            Char(0x1A).escape() shouldBe "\\u001a"
            Char(0x1F).escape() shouldBe "\\u001f"
        }

        test("printable and non-ascii characters are kept") {
            ' '.escape() shouldBe " "
            'A'.escape() shouldBe "A"
            Char(0x7F).escape() shouldBe Char(0x7F).toString()
            'ф'.escape() shouldBe "ф"
        }

        test("multiline keeps whitespace literal") {
            '\n'.escape(multiline = true) shouldBe "\n"
            '\r'.escape(multiline = true) shouldBe "\r"
            '\t'.escape(multiline = true) shouldBe "\t"
            Char(0x1A).escape(multiline = true) shouldBe "\\u001a"
        }

        test("every ascii character round-trips") {
            for (code in 0x00..0x7F) {
                val char = code.toChar()
                char.escape().unescape() shouldBe char.toString()
            }
        }
    }

    testSuite("unescape") {
        test("named sequences") {
            "\\b\\t\\n\\f\\r".unescape() shouldBe "\b\t\n${Char(FORM_FEED)}\r"
            "\\\"\\\\\\/".unescape() shouldBe "\"\\/"
        }

        test("short unicode escape") {
            "\\u0041".unescape() shouldBe "A"
            "a\\u0042c".unescape() shouldBe "aBc"
        }

        test("long unicode escape consumes all ten characters") {
            "\\U0001F600".unescape() shouldBe "😀"
            "a\\U0001F600c".unescape() shouldBe "a😀c"
            "\\U00000041".unescape() shouldBe "A"
        }

        test("malformed escapes are kept verbatim") {
            "\\uZZZZ".unescape() shouldBe "\\uZZZZ"
            "\\u12".unescape() shouldBe "\\u12"
            "\\UFFFFFFFF".unescape() shouldBe "\\UFFFFFFFF"
            "\\q".unescape() shouldBe "\\q"
            "trailing\\".unescape() shouldBe "trailing\\"
        }
    }
}
