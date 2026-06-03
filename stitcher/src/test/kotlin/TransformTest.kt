import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.stitcher.replacement.Replacement
import io.kotest.assertions.shouldFail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import util.process

class TransformTest : FreeSpec({
    "replacements" - {
        "simple string" {
            val content = """
                Hello World!
            """.trimIndent()
            val expected = """
                Hello Lace!
            """.trimIndent()
            process(content) {
                replacements.string("World", "Lace")
            } shouldBe expected
        }

        "contained string" {
            val content = """
                a.b.c
            """.trimIndent()
            val expected = """
                a.b.c
            """.trimIndent()
            process(content) {
                replacements.string("b", "a.b.c")
            } shouldBe expected
        }

        "longest string" {
            val content = """
                a.b.c
            """.trimIndent()
            val expected = """
                e.c
            """.trimIndent()
            process(content) {
                replacements.string("a", "d")
                replacements.string("a.b", "e")
            } shouldBe expected
        }

        "active identifier" {
            val content = """
                //~ example
                a.b.c
            """.trimIndent()
            val expected = """
                //~ example
                a.d.c
            """.trimIndent()
            process(content) {
                replacements.string("b", "d", "example")
            } shouldBe expected
        }

        "inactive identifier" {
            val content = """
                a.b.c
            """.trimIndent()
            val expected = """
                a.b.c
            """.trimIndent()
            process(content) {
                replacements.string("b", "d", "example")
            } shouldBe expected
        }

        // Matches inside comments should be replaced
        "commented values" {
            val content = """
                a.b.c
                // a.b.c
            """.trimIndent()
            val expected = """
                a.d.c
                // a.d.c
            """.trimIndent()
            process(content) {
                replacements.string("b", "d")
            } shouldBe expected
        }

        // Matches in what is about to be processed should be replaced as well
        "commented out values" {
            val content = """
                //? if it
                a.b.c
            """.trimIndent()
            val expected = """
                //? if it
                //a.d.c
            """.trimIndent()
            process(content) {
                constants["it"] = false
                replacements.string("b", "d")
            } shouldBe expected
        }

        // Matches in code that was uncommented should be replaced
        "uncommented values" {
            val content = """
                //? if it
                //a.b.c
            """.trimIndent()
            val expected = """
                //? if it
                a.d.c
            """.trimIndent()
            process(content) {
                constants["it"] = true
                replacements.string("b", "d")
            } shouldBe expected
        }

        "string-regex overlap" {
            val content = "a.b.c.d"
            val expected = "a.2.c.d"
            process(content) {
                replacements.string("a.b.c", "a.2.c")
                replacements.regex("b\\.c\\.d", "!!!")
            } shouldBe expected

            process(content) {
                replacements.string("b.c.d", "!!!")
                replacements.regex("a\\.b\\.c", "a.2.c")
            } shouldBe expected
        }

        "toggle multiple ids" {
            val content = """
                a.b.c.d.e
                //~ !ab de
                a.b.c.d.e
            """.trimIndent()
            val expected = """
                1.2.c.d.e
                //~ !ab de
                a.b.c.4.5
            """.trimIndent()
            process(content) {
                replacements.string("a.b", "1.2", id = "ab", true)
                replacements.string("d.e", "4.5", id = "de")
            } shouldBe expected
        }

        "ignore subsequent toggles" {
            val content = """
                //~ !my_id
                //~ !my_id
                a.b.c
            """.trimIndent()
            val expected = """
                //~ !my_id
                //~ !my_id
                a.b.c
            """.trimIndent()
            process(content) {
                replacements.string("a.b", "1.2", id = "my_id")
            } shouldBe expected
        }

        "basic local replacement" {
            val content = """
                a.b.c.d.e
                //~ if true 'b.c' -> '2.3' {
                a.b.c.d.e
                a.b.c.d.e
                //~}
            """.trimIndent()
            val expected = """
                a.b.c.d.e
                //~ if true 'b.c' -> '2.3' {
                a.2.3.d.e
                a.2.3.d.e
                //~}
            """.trimIndent()
            process(content) shouldBe expected
        }

        "nested local replacement" {
            val content = """
                a.b.c.d.e
                //~ if true 'b.c' -> '2.3' {
                a.b.c.d.e
                //~ if true 'd.e' -> '4.5'
                a.b.c.d.e
                a.b.c.d.e
                //~}
            """.trimIndent()
            val expected = """
                a.b.c.d.e
                //~ if true 'b.c' -> '2.3' {
                a.2.3.d.e
                //~ if true 'd.e' -> '4.5'
                a.2.3.4.5
                a.2.3.d.e
                //~}
            """.trimIndent()
            process(content) shouldBe expected
        }

        "overwrite global replacements".config(enabled = false) {
            val content = """
                a.b.c.d.e
                //~ if true 'b.c' -> '6.9'
                a.b.c.d.e
            """.trimIndent()
            val expected = """
                a.2.3.d.e
                //~ if true 'b.c' -> '6.9'
                a.6.9.d.e
            """.trimIndent()
            process(content) {
                replacements.string("b.c", "2.3")
            } shouldBe expected
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/40
        "ignore replaced patterns" {
            val content = """
                //~ if true RenderType -> RenderTypes
                RenderTypes
            """.trimIndent()
            val expected = """
                //~ if true RenderType -> RenderTypes
                RenderTypes
            """.trimIndent()
            process(content) shouldBe expected
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/43
        "replace across comments" {
            val content = """
                My/*Map*/Codec
            """.trimIndent()
            val expected = """
                MyMapCodec
            """.trimIndent()
            process(content) {
                replacements.string("/*Map*/Codec", "MapCodec")
            } shouldBe expected
        }
    }

    "conditions" - {
        "disable segment" {
            val content = """
                //? if false
                ...
            """.trimIndent()
            val expected = """
                //? if false
                //...
            """.trimIndent()
            process(content) {
                constants["true"] = true
                constants["false"] = false
            } shouldBe expected
        }

        "enable segment" {
            val content = """
                //? if true
                //...
            """.trimIndent()
            val expected = """
                //? if true
                ...
            """.trimIndent()
            process(content) {
                constants["true"] = true
                constants["false"] = false
            } shouldBe expected
        }

        "unary negation" {
            val content = """
                //? if !true
                ...
            """.trimIndent()
            val expected = """
                //? if !true
                //...
            """.trimIndent()
            process(content) {
                constants["true"] = true
                constants["false"] = false
            } shouldBe expected
        }

        "boolean or" {
            val content = """
                //? if false || true
                //...
            """.trimIndent()
            val expected = """
                //? if false || true
                ...
            """.trimIndent()
            process(content) {
                constants["true"] = true
                constants["false"] = false
            } shouldBe expected
        }

        "boolean and" {
            val content = """
                //? if false && true
                ...
            """.trimIndent()
            val expected = """
                //? if false && true
                //...
            """.trimIndent()
            process(content) {
                constants["true"] = true
                constants["false"] = false
            } shouldBe expected
        }

        "single predicate" {
            val content = """
                //? if >1
                ...
            """.trimIndent()
            val expected = """
                //? if >1
                //...
            """.trimIndent()
            process(content) {
                dependencies[""] = SemanticVersion(1)
            } shouldBe expected
        }

        "multiple predicates" {
            val content = """
                //? if >1 <3
                //...
            """.trimIndent()
            val expected = """
                //? if >1 <3
                //...
            """.trimIndent()
            process(content) {
                dependencies[""] = SemanticVersion(3)
            } shouldBe expected
        }

        "named predicates" {
            val content = """
                //? if id: >1 <3
                //...
            """.trimIndent()
            val expected = """
                //? if id: >1 <3
                //...
            """.trimIndent()
            process(content) {
                dependencies["id"] = SemanticVersion(3)
            } shouldBe expected
        }

        "predicate operator precedence" {
            val content = """
                //? if !=1 <3
                //...
            """.trimIndent()
            val expected = """
                //? if !=1 <3
                ...
            """.trimIndent()
            process(content) {
                dependencies[""] = SemanticVersion(2)
            } shouldBe expected
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/31
        "spaced string lookup bug" {
            val content = "/*? if <1.21.9 >>*/ /*,0*/ )"
            val expected = "/*? if <1.21.9 >>*/ ,0 )"
            process(content) {
                dependencies[""] = SemanticVersion(1, 21, 8)
            } shouldBe expected
        }
    }

    "swaps" - {
        // https://codeberg.org/stonecutter/stonecutter/issues/32
        "insert empty" {
            val content = "/*$ id {*//*$}*/"
            val expected = "/*$ id {*/inserted/*$}*/"
            process(content) {
                swaps["id"] = "inserted"
            } shouldBe expected
        }

        "oob placeholder" {
            val content = "/*$ id 'hello' {*/.../*$}*/"
            shouldFail {
                process(content) {
                    swaps["id"] = "$1 $2"
                }
            }
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/34
        "weird parameter overflow bug" {
            val content = "/*$ open_url '\"example\"' {*/ClickEvent.OpenUrl(URI.create(\"example\"))/*$}*/"
            val expected = "/*$ open_url '\"example\"' {*/ClickEvent(ClickEvent.Action.OPEN_URL, \"example\")/*$}*/"
            process(content) {
                swaps["open_url"] = "ClickEvent(ClickEvent.Action.OPEN_URL, $1)"
            } shouldBe expected
        }

        "basic local swap" {
            val content = """
                //$ if param 'method1()' else 'method2()'
                    ???
            """.trimIndent()

            process(content) {
                constants["param"] = true
            } shouldBe """
                //$ if param 'method1()' else 'method2()'
                    method1()
            """.trimIndent()

            process(content) {
                constants["param"] = false
            } shouldBe """
                //$ if param 'method1()' else 'method2()'
                    method2()
            """.trimIndent()
        }

        "horrible local swap" {
            val content = """
                //$ if param1 'method1()' elif param2 'method2()' else 'method3()'
                    ???
            """.trimIndent()

            process(content) {
                constants["param1"] = true
                constants["param2"] = true
            } shouldBe """
                //$ if param1 'method1()' elif param2 'method2()' else 'method3()'
                    method1()
            """.trimIndent()

            process(content) {
                constants["param1"] = false
                constants["param2"] = true
            } shouldBe """
                //$ if param1 'method1()' elif param2 'method2()' else 'method3()'
                    method2()
            """.trimIndent()

            process(content) {
                constants["param1"] = false
                constants["param2"] = false
            } shouldBe """
                //$ if param1 'method1()' elif param2 'method2()' else 'method3()'
                    method3()
            """.trimIndent()
        }
    }

    "deep nesting" {
        val content = """
                /*? if false {*/
                    ...
                    /*? if false {*/
                        ...
                        /*? if false {*/
                            ...
                            /*? if false {*/
                                ...
                                /*? if false {*/
                                    ...
                                    /*? if false {*/
                                        ...
                                        /*? if false {*/
                                            ...
                                            /*? if false {*/
                                                ...
                                                /*? if false {*/
                                                    ...
                                                    /*? if false {*/
                                                        ...
                                                        /*? if false {*/
                                                            ...
                                                        /*?}*/
                                                    /*?}*/
                                                /*?}*/
                                            /*?}*/
                                        /*?}*/
                                    /*?}*/
                                /*?}*/
                            /*?}*/
                        /*?}*/
                    /*?}*/
                /*?}*/
            """.trimIndent()
        val expected = """
                /*? if false {*/
                    /*...
                    /^? if false {^/
                        /^...
                        /^¹? if false {¹^/
                            /^¹...
                            /^²? if false {²^/
                                /^²...
                                /^³? if false {³^/
                                    /^³...
                                    /^⁴? if false {⁴^/
                                        /^⁴...
                                        /^⁵? if false {⁵^/
                                            /^⁵...
                                            /^⁶? if false {⁶^/
                                                /^⁶...
                                                /^⁷? if false {⁷^/
                                                    /^⁷...
                                                    /^⁸? if false {⁸^/
                                                        /^⁸...
                                                        /^⁹? if false {⁹^/
                                                            /^⁹...
                                                        ⁹^//^⁹?}⁹^/
                                                    ⁸^//^⁸?}⁸^/
                                                ⁷^//^⁷?}⁷^/
                                            ⁶^//^⁶?}⁶^/
                                        ⁵^//^⁵?}⁵^/
                                    ⁴^//^⁴?}⁴^/
                                ³^//^³?}³^/
                            ²^//^²?}²^/
                        ¹^//^¹?}¹^/
                    ^//^?}^/
                *//*?}*/
            """.trimIndent()
        process(content) {
            constants["false"] = false
        } shouldBe expected
    }
})