@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.StitcherParameters
import io.kotest.matchers.shouldBe
import matcher.assemble
import matcher.params
import org.intellij.lang.annotations.Language
import java.util.regex.Pattern

private const val PARTY = "🎉"

val GlobalReplacementTest by testSuite {
    testSuite("string") {
        test("replace a single occurrence") {
            "val a = \"a.b.c\"".shouldAssembleTo(params { string("b", "d") }, "val a = \"a.d.c\"")
        }

        test("replace every occurrence") {
            "a.b.c b.b".shouldAssembleTo(params { string("b", "d") }, "a.d.c d.d")
        }

        test("prefer the longest pattern") {
            "a.b.c".shouldAssembleTo(
                params {
                    string("a", "d")
                    string("a.b", "e")
                },
                "e.c"
            )
        }

        test("replace inside comments") {
            "a.b.c\n// a.b.c".shouldAssembleTo(params { string("b", "d") }, "a.d.c\n// a.d.c")
        }

        test("leave the text alone without a match") {
            "val a = 1".shouldAssembleTo(params { string("b", "d") }, "val a = 1")
        }

        test("skip an identifier that is not enabled") {
            "a.b.c".shouldAssembleTo(params { string("b", "d", "example") }, "a.b.c")
        }

        test("apply an identifier enabled by a marker") {
            "//~ example\na.b.c".shouldAssembleTo(params { string("b", "d", "example") }, "//~ example\na.d.c")
        }
    }

    testSuite("regex") {
        test("replace every match of a quantified pattern") {
            "v1 v22 v333".shouldAssembleTo(params { regex("""v\d+""", "vX") }, "vX vX vX")
        }

        test("leave the text alone without a match") {
            "val a = 1".shouldAssembleTo(params { regex("""v\d+x""", "vX") }, "val a = 1")
        }

        test("honour compilation flags") {
            "Hello hello".shouldAssembleTo(
                params { regex("hello", "hi", flags = Pattern.CASE_INSENSITIVE) },
                "hi hi"
            )
        }

        test("skip an identifier that is not enabled") {
            "v1".shouldAssembleTo(params { regex("""v\d""", "vX", "example") }, "v1")
        }

        test("apply an identifier enabled by a marker") {
            "//~ example\nv1".shouldAssembleTo(params { regex("""v\d""", "vX", "example") }, "//~ example\nvX")
        }
    }

    testSuite("perl") {
        test("replace every match of a quantified pattern") {
            "v1 v22 v333".shouldAssembleTo(params { perl("""v\d+""", "vX") }, "vX vX vX")
        }

        test("expand a capture group into the target") {
            "MOD_ALPHA_LEGACY MOD_BETA_LEGACY".shouldAssembleTo(
                params { perl("""MOD_(\w+)_LEGACY""", "MOD_\$1_MODERN") },
                "MOD_ALPHA_MODERN MOD_BETA_MODERN"
            )
        }

        // Pcre4j reports a failure to match by returning an empty result and leaving the previous
        // match in the output vector, which used to leave the executor walking the same offset forever.
        test("leave the text alone without a match") {
            "val a = 1".shouldAssembleTo(params { perl("""v\d+x""", "vX") }, "val a = 1")
        }

        test("advance past a zero-width match") {
            "abcabc".shouldAssembleTo(params { perl("(?=b)", "-") }, "a-bca-bc")
        }

        test("skip an identifier that is not enabled") {
            "v1".shouldAssembleTo(params { perl("""v\d""", "vX", "example") }, "v1")
        }

        test("apply an identifier enabled by a marker") {
            "//~ example\nv1".shouldAssembleTo(params { perl("""v\d""", "vX", "example") }, "//~ example\nvX")
        }
    }

    testSuite("non-ASCII offsets") {
        test("string pattern is non-ASCII") {
            "val a = \"привіт\"".shouldAssembleTo(params { string("привіт", "hello") }, "val a = \"hello\"")
        }

        test("string target is non-ASCII") {
            "val a = \"hello\"".shouldAssembleTo(params { string("hello", "привіт") }, "val a = \"привіт\"")
        }

        // `\w` and friends stay ASCII-only in both engines, so a class matching non-ASCII has to spell it out.
        test("regex pattern is non-ASCII") {
            "val a = \"привіт\"".shouldAssembleTo(params { regex("""прив[іт]+""", "hello") }, "val a = \"hello\"")
        }

        test("regex match follows non-ASCII text") {
            "// café — naïve 日本語\nval a = v1".shouldAssembleTo(
                params { regex("""v\d""", "vX") },
                "// café — naïve 日本語\nval a = vX"
            )
        }

        test("regex match follows a supplementary-plane character") {
            "val e = \"$PARTY\"\nval a = v1".shouldAssembleTo(
                params { regex("""v\d""", "vX") },
                "val e = \"$PARTY\"\nval a = vX"
            )
        }

        test("perl pattern is non-ASCII") {
            "val a = \"привіт\"".shouldAssembleTo(params { perl("""прив[іт]+""", "hello") }, "val a = \"hello\"")
        }

        // PCRE2 matches the subject as raw bytes unless it is compiled for UTF, which would count the
        // two bytes of `é` as two characters and leave `a` short of the quantifier.
        test("perl quantifier counts characters") {
            "éa".shouldAssembleTo(params { perl(".{2}", "X") }, "X")
        }

        test("perl match follows non-ASCII text") {
            "// café — naïve 日本語\nval a = v1".shouldAssembleTo(
                params { perl("""v\d""", "vX") },
                "// café — naïve 日本語\nval a = vX"
            )
        }

        test("perl match follows a supplementary-plane character") {
            "val e = \"$PARTY\"\nval a = v1".shouldAssembleTo(
                params { perl("""v\d""", "vX") },
                "val e = \"$PARTY\"\nval a = vX"
            )
        }

        test("perl matches on both sides of non-ASCII text") {
            "v1 // café — 日本語 $PARTY\nv2".shouldAssembleTo(
                params { perl("""v\d""", "vX") },
                "vX // café — 日本語 $PARTY\nvX"
            )
        }

        test("perl capture group spans non-ASCII text") {
            "val a = \"MOD_привіт_LEGACY\"".shouldAssembleTo(
                params { perl("""MOD_([^_]+)_LEGACY""", "MOD_\$1_MODERN") },
                "val a = \"MOD_привіт_MODERN\""
            )
        }
    }
}

context(scope: Test.ExecutionScope)
private fun @receiver:Language("kotlin") String.shouldAssembleTo(params: StitcherParameters, @Language("kotlin") expected: String) {
    scope.assemble(this, params) shouldBe expected
}
