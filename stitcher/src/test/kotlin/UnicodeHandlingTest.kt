@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.antlr.charStream
import dev.kikugie.stitcher.document.io.LineNormalizingReader
import dev.kikugie.stitcher.document.io.LineNormalizingWriter
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import matcher.assemble
import matcher.params
import org.antlr.v4.runtime.IntStream
import org.antlr.v4.runtime.misc.Interval
import org.intellij.lang.annotations.Language
import java.io.StringWriter

private const val NBSP = "\u00A0"
private const val LS = "\u2028"
private const val PS = "\u2029"
private const val NEL = "\u0085"
private const val BOM = "\uFEFF"
private const val ACUTE = "\u0301"
private const val PARTY = "🎉"
private const val TECHNOLOGIST = "👩‍💻"

val UnicodeHandlingTest by testSuite {
    testSuite("basic multilingual plane") {
        test("content without directives is preserved verbatim") {
            "val привіт = \"你好\"" shouldAssembleTo "val привіт = \"你好\""
        }

        test("comment out a line with Cyrillic text") {
            """
                //? if false
                val привіт = 1
                val two = 2
            """.trimIndent() shouldAssembleTo """
                //? if false
                //val привіт = 1
                val two = 2
            """.trimIndent()
        }

        test("uncomment a line with Cyrillic text") {
            """
                //? if true
                //val привіт = 1
            """.trimIndent() shouldAssembleTo """
                //? if true
                val привіт = 1
            """.trimIndent()
        }

        test("comment out a line with CJK text") {
            """
                //? if false
                val 漢字 = 1
                val two = 2
            """.trimIndent() shouldAssembleTo """
                //? if false
                //val 漢字 = 1
                val two = 2
            """.trimIndent()
        }

        test("uncomment a line with CJK text") {
            """
                //? if true
                //val 漢字 = 1
            """.trimIndent() shouldAssembleTo """
                //? if true
                val 漢字 = 1
            """.trimIndent()
        }

        test("uncomment a block with Greek text") {
            """
                //? if true {
                /*val α = 1
                val β = 2
                *///?}
            """.trimIndent() shouldAssembleTo """
                //? if true {
                val α = 1
                val β = 2
                //?}
            """.trimIndent()
        }

        test("comment out a block with nested conditions and Greek text") {
            """
                //? if false {
                val α = 1
                //? if true
                val β = 2
                //?}
            """.trimIndent() shouldAssembleTo """
                //? if false {
                /*val α = 1
                //? if true
                val β = 2
                *///?}
            """.trimIndent()
        }

        test("flip an else chain with Greek text") {
            """
                //? if false
                val α = 1
                //? else
                //val β = 2
            """.trimIndent() shouldAssembleTo """
                //? if false
                //val α = 1
                //? else
                val β = 2
            """.trimIndent()
        }

        test("comment out right-to-left text") {
            """
                //? if false
                val a = "שלום"
                val b = 2
            """.trimIndent() shouldAssembleTo """
                //? if false
                //val a = "שלום"
                val b = 2
            """.trimIndent()
        }

        test("flatten a block comment holding Cyrillic text") {
            """
                //? if false {
                /* замітка */
                val two = 2
                //?}
            """.trimIndent() shouldAssembleTo """
                //? if false {
                /*/^ замітка ^/
                val two = 2
                *///?}
            """.trimIndent()
        }
    }

    testSuite("combining marks and invisible characters") {
        test("combining diacritic stays attached to its base character") {
            "//? if false\nval cafe$ACUTE = 1" shouldAssembleTo "//? if false\n//val cafe$ACUTE = 1"
        }

        test("no-break space is ordinary content") {
            "//? if false\nval${NBSP}one = 1" shouldAssembleTo "//? if false\n//val${NBSP}one = 1"
        }

        test("byte order mark before a directive is preserved") {
            "$BOM//? if false\nval one = 1" shouldAssembleTo "$BOM//? if false\n//val one = 1"
        }

        test("byte order mark does not hide the directive") {
            "$BOM//? if true\n//val one = 1" shouldAssembleTo "$BOM//? if true\nval one = 1"
        }

        // The scanner ends a line comment on `\r` / `\n` only, so the Unicode separators below stay
        // inside the commented-out line instead of splitting it.
        test("line separator does not end a line") {
            "//? if false\nval a = 1${LS}val b = 2\nval c = 3" shouldAssembleTo
                "//? if false\n//val a = 1${LS}val b = 2\nval c = 3"
        }

        test("paragraph separator does not end a line") {
            "//? if false\nval a = 1${PS}val b = 2\nval c = 3" shouldAssembleTo
                "//? if false\n//val a = 1${PS}val b = 2\nval c = 3"
        }

        test("next line does not end a line") {
            "//? if false\nval a = 1${NEL}val b = 2\nval c = 3" shouldAssembleTo
                "//? if false\n//val a = 1${NEL}val b = 2\nval c = 3"
        }
    }

    testSuite("directive payloads") {
        test("lookup literal with Cyrillic text") {
            """
                //? if false >> 'привіт'
                val one = 1
                val привіт = 2
            """.trimIndent() shouldAssembleTo """
                //? if false >> 'привіт'
                /*val one = 1
                val */привіт = 2
            """.trimIndent()
        }

        test("enabled lookup with Cyrillic literal") {
            """
                //? if true >> 'val ї'
                //val one = 1
                val ї = 2
            """.trimIndent() shouldAssembleTo """
                //? if true >> 'val ї'
                val one = 1
                val ї = 2
            """.trimIndent()
        }

        test("local replacement with a Cyrillic pattern") {
            """
                //~ if true 'привіт' -> 'hello'
                val a = "привіт"
            """.trimIndent() shouldAssembleTo """
                //~ if true 'привіт' -> 'hello'
                val a = "hello"
            """.trimIndent()
        }

        test("string replacement with a Cyrillic source") {
            "val a = \"привіт\"".shouldAssembleTo(params { string("привіт", "hello") }, "val a = \"hello\"")
        }

        test("regex replacement with a Cyrillic source") {
            "val a = \"привіт\"".shouldAssembleTo(params { regex("привіт", "hello") }, "val a = \"hello\"")
        }

        test("perl replacement with a Cyrillic source") {
            "val a = \"привіт\"".shouldAssembleTo(params { perl("привіт", "hello") }, "val a = \"hello\"")
        }

        test("replacement target may be non-ASCII") {
            "val a = \"hello\"".shouldAssembleTo(params { string("hello", "привіт") }, "val a = \"привіт\"")
        }

        // Replacement and swap payloads are written out literally rather than being addressed by
        // index, so a supplementary-plane character in a target is safe.
        test("replacement target may be supplementary-plane") {
            "val a = \"hello\"".shouldAssembleTo(params { string("hello", PARTY) }, "val a = \"$PARTY\"")
        }

        test("swap value with Cyrillic text") {
            val params = params { swaps["id"] = "val привіт = 1" }
            "//\$ id\n???\nval after = 1".shouldAssembleTo(params, "//\$ id\nval привіт = 1\nval after = 1")
        }

        test("swap value with a supplementary-plane character") {
            val params = params { swaps["id"] = "val a = \"$PARTY\"" }
            "//\$ id\n???\nval after = 1".shouldAssembleTo(params, "//\$ id\nval a = \"$PARTY\"\nval after = 1")
        }

        test("swap argument with Cyrillic text") {
            val params = params { swaps["id"] = "greet(\$1)" }
            "/*\$ id 'привіт світ' {*/old/*\$}*/"
                .shouldAssembleTo(params, "/*\$ id 'привіт світ' {*/greet(привіт світ)/*\$}*/")
        }
    }

    // `IDENTIFIER` is `[a-zA-Z_][a-zA-Z0-9_-]*`, so names are ASCII-only even where the surrounding
    // content is not. Only quoted literals accept arbitrary text.
    testSuite("identifiers are ASCII-only") {
        test("non-ASCII constant is rejected") {
            shouldThrowAny { assemble("//? if прив\nval one = 1") }
        }

        test("accented constant is rejected") {
            shouldThrowAny { assemble("//? if cafe$ACUTE\nval one = 1") }
        }

        test("non-ASCII replacement toggle is rejected") {
            shouldThrowAny { assemble("//~ ідентифікатор\nval a = 1") }
        }
    }

    // Line normalisation walks the text one UTF-16 char at a time, so it must not split or reorder
    // the halves of a surrogate pair.
    testSuite("line normalisation") {
        test("reader preserves a surrogate pair between normalised breaks") {
            LineNormalizingReader("a\r\n$PARTY\rb".reader()).readText() shouldBe "a\n$PARTY\nb"
        }

        test("reader preserves a surrogate pair read one char at a time") {
            val reader = LineNormalizingReader("a\r\n$PARTY\rb".reader())
            val buffer = CharArray(1)
            buildString {
                while (reader.read(buffer, 0, 1) != -1) append(buffer[0])
            } shouldBe "a\n$PARTY\nb"
        }

        test("writer preserves a surrogate pair between denormalised breaks") {
            val destination = StringWriter()
            LineNormalizingWriter(destination, "\r\n").use { it.write("a\n$PARTY\nb") }
            destination.toString() shouldBe "a\r\n$PARTY\r\nb"
        }
    }

    // The stream feeding every scan addresses text in the same units as the documents its tokens are
    // matched against — UTF-16 chars, not the code points ANTLR's own factories count.
    testSuite("char stream") {
        test("size counts UTF-16 chars") {
            "a${PARTY}b".charStream().size() shouldBe 4
        }

        test("lookahead walks code units") {
            val stream = "a${PARTY}b".charStream()
            stream.LA(1) shouldBe 'a'.code
            stream.consume()
            stream.LA(1) shouldBe PARTY[0].code
            stream.LA(2) shouldBe PARTY[1].code
            stream.LA(3) shouldBe 'b'.code
            stream.LA(-1) shouldBe 'a'.code
        }

        test("lookahead outside the text is EOF") {
            val stream = "a$PARTY".charStream()
            stream.LA(4) shouldBe IntStream.EOF
            stream.LA(-1) shouldBe IntStream.EOF
        }

        test("text is sliced on UTF-16 boundaries") {
            "a${PARTY}b".charStream().getText(Interval.of(1, 2)) shouldBe PARTY
        }

        test("text past the end is clamped") {
            "a$PARTY".charStream().getText(Interval.of(0, 100)) shouldBe "a$PARTY"
        }

        test("seek clamps to the end") {
            val stream = "a$PARTY".charStream()
            stream.seek(100)
            stream.index() shouldBe 3
            stream.LA(1) shouldBe IntStream.EOF
        }
    }

    // Each case here is a basic-multilingual-plane case from above with an astral character swapped in,
    // so a code point above `U+FFFF` must not shift the offsets of everything following it.
    testSuite("supplementary plane") {
        test("content without directives is preserved verbatim") {
            "val a = \"$PARTY\"" shouldAssembleTo "val a = \"$PARTY\""
        }

        test("comment out a line with an emoji") {
            """
                //? if false
                val a = "$PARTY"
                val two = 2
            """.trimIndent() shouldAssembleTo """
                //? if false
                //val a = "$PARTY"
                val two = 2
            """.trimIndent()
        }

        test("uncomment a line with an emoji") {
            """
                //? if true
                //val a = "$PARTY"
            """.trimIndent() shouldAssembleTo """
                //? if true
                val a = "$PARTY"
            """.trimIndent()
        }

        test("an emoji before a directive does not shift it") {
            """
                val e = "$PARTY"
                //? if false
                val one = 1
            """.trimIndent() shouldAssembleTo """
                val e = "$PARTY"
                //? if false
                //val one = 1
            """.trimIndent()
        }

        test("comment out a block with an emoji") {
            """
                //? if false {
                val a = "$PARTY"
                val b = 2
                //?}
            """.trimIndent() shouldAssembleTo """
                //? if false {
                /*val a = "$PARTY"
                val b = 2
                *///?}
            """.trimIndent()
        }

        test("uncomment a block with an emoji") {
            """
                //? if true {
                /*val a = "$PARTY"
                val b = 2
                *///?}
            """.trimIndent() shouldAssembleTo """
                //? if true {
                val a = "$PARTY"
                val b = 2
                //?}
            """.trimIndent()
        }

        test("comment out a zero-width joiner sequence") {
            """
                //? if false
                val a = "$TECHNOLOGIST"
                val b = 2
            """.trimIndent() shouldAssembleTo """
                //? if false
                //val a = "$TECHNOLOGIST"
                val b = 2
            """.trimIndent()
        }

        test("an emoji in a plain comment does not truncate the file") {
            "// $PARTY note\nval a = 1" shouldAssembleTo "// $PARTY note\nval a = 1"
        }

        test("lookup literal with an emoji") {
            """
                //? if false >> '$PARTY'
                val one = 1
                val x = "$PARTY"
            """.trimIndent() shouldAssembleTo """
                //? if false >> '$PARTY'
                /*val one = 1
                val x = "*/$PARTY"
            """.trimIndent()
        }

        test("string replacement with an emoji source") {
            "val a = \"$PARTY\"".shouldAssembleTo(params { string(PARTY, "party") }, "val a = \"party\"")
        }

        // Swap templates are scanned for `$n` placeholders, whose offsets slice the template itself.
        test("swap placeholder after an emoji") {
            val params = params { swaps["id"] = "greet(\"$PARTY\", \$1)" }
            "/*\$ id 'name' {*/old/*\$}*/"
                .shouldAssembleTo(params, "/*\$ id 'name' {*/greet(\"$PARTY\", name)/*\$}*/")
        }

        // Restoring `/^ ^/` placeholders to real delimiters is driven by offsets into the comment body.
        test("nested comment markers restored around an emoji") {
            """
                //? if true {
                /*/^ $PARTY note ^/
                val a = 1
                *///?}
            """.trimIndent() shouldAssembleTo """
                //? if true {
                /* $PARTY note */
                val a = 1
                //?}
            """.trimIndent()
        }
    }
}

context(scope: Test.ExecutionScope)
private infix fun @receiver:Language("kotlin") String.shouldAssembleTo(@Language("kotlin") expected: String): Unit =
    shouldAssembleTo(params(), expected)

context(scope: Test.ExecutionScope)
private fun @receiver:Language("kotlin") String.shouldAssembleTo(params: StitcherParameters, @Language("kotlin") expected: String) {
    scope.assemble(this, params) shouldBe expected
}
