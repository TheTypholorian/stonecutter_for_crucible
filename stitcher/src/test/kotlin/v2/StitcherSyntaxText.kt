package v2

import dev.kikugie.stitcher.parser.SYNTAX_GROUP
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import v2.impl.ProcessTestSetup
import v2.impl.config

class StitcherSyntaxText : FunSpec({
    context("conditions") {
        test("implicit line opener").config<ProcessTestSetup> {
            input = "//? false\n..."
        }

        test("explicit line opener").config<ProcessTestSetup> {
            input = "//? if false\n..."
        }

        test("no expression line opener").config<ProcessTestSetup> {
            input = "//?\n..."

            errors {
                shouldBeSingleton { it.id shouldBe SYNTAX_GROUP.child("antlr-error") }
            }
        }

        test("implicit closed opener").config<ProcessTestSetup> {
            input = "//? false {\n...\n//?}"
        }

        test("explicit closed opener").config<ProcessTestSetup> {
            input = "//? if false {\n...\n//?}"
        }

        test("scope opener after duplicate expression").config<ProcessTestSetup> {
            input = """
                //? if false false {
                ...
                //?}
            """.trimIndent()

            errors { shouldBeSingleton() }
        }

        test("expression after scope closer").config<ProcessTestSetup> {
            input = "//? if true {\n//?} false"
        }

        test("expression after scope closer & else").config<ProcessTestSetup> {
            input = "//? if true {\n//?} else false"
        }

        test("expression after scope closer & else if").config<ProcessTestSetup> {
            input = "//? if true {\n//?} else if false"
        }

        test("expression after scope closer & elif").config<ProcessTestSetup> {
            input = "//? if true {\n//?} elif false"
        }

        test("expression after scope closer & if").config<ProcessTestSetup> {
            input = "//? if true {\n//?} if false"

            errors { shouldBeSingleton() }
        }

        test("empty after else").config<ProcessTestSetup> {
            input = "//? if true\n//? else"

            errors { shouldBeSingleton() }
        }

        test("expression after else").config<ProcessTestSetup> {
            input = "//? if true\n//? else false"

            errors { shouldBeSingleton() }
        }

        test("expression after else if").config<ProcessTestSetup> {
            input = "//? if true\n//? else if false"

            errors { shouldBeSingleton() }
        }

        test("expression after elif").config<ProcessTestSetup> {
            input = "//? if true\n//? elif false"

            errors { shouldBeSingleton() }
        }

        // TODO: make more of it
    }

    context("swaps") {
        test("line identifier").config<ProcessTestSetup> {
            swaps["var"] = "!"
            input = "//$ var\n..."
        }

        test("scoped identifier").config<ProcessTestSetup> {
            swaps["var"] = "!"
            input = "//$ var {\n...//$}"
        }

        test("line identifier with args").config<ProcessTestSetup> {
            swaps["var"] = "$1, $2, $3"
            input = "//$ var abc '123', '!!!',\n..."
        }

        test("local line if- swap").config<ProcessTestSetup> {
            input = "//$ if false 'a'"
            errors { shouldBeSingleton() }
        }

        test("local line if-else swap").config<ProcessTestSetup> {
            input = "//$ if false 'a' else 'b'"
        }

        test("local line if-elif-else swap").config<ProcessTestSetup> {
            input = "//$ if false 'a' elif false 'b' else 'c'"
        }

        test("local scoped if-else swap").config<ProcessTestSetup> {
            input = "//$ if false 'a' else 'b' {\n...\n//$}"
        }
    }

    context("replacements") {
        test("toggles").config<ProcessTestSetup> {
            replacements += string("a", "a", id = "id1")
            replacements += string("b", "b", id = "id2")
            replacements += string("c", "c", id = "id3")

            input = "//~ id1 !id2, id3,"
        }

        test("local line").config<ProcessTestSetup> {
            input = "//~ if true 'a' -> 'b'"
        }

        test("local scoped").config<ProcessTestSetup> {
            input = "//~ if true 'a' -> 'b' {\n...\n//~}"
        }

        test("local lookup").config<ProcessTestSetup> {
            input = "//~ if true 'a' -> 'b' >> 'c'\n... c ..."
        }

        test("local named").config<ProcessTestSetup> {
            input = "//~ if true 'a' -> 'b' as id\n..."
        }

        test("local multiple").config<ProcessTestSetup> {
            input = """
                /*~ if true
                    'a' -> 'b'
                    'c' -> 'd',
                    'e' -> 'f',
                    as id
                */
                ...
            """.trimIndent()
        }
    }
})