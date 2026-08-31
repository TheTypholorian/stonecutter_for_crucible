@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.stitcher.StitcherParameters
import io.kotest.matchers.shouldBe
import matcher.assemble
import matcher.params
import org.intellij.lang.annotations.Language

val FileAstAssemblerTest by testSuite {
    testSuite("condition scopes") {
        test("disable scoped with multi-line comment") {
            """
                //? if false {
                val one = 1
                val two = 2
                //?}
            """.trimIndent() shouldAssembleTo """
                //? if false {
                /*val one = 1
                val two = 2
                *///?}
            """.trimIndent()
        }

        test("enable scoped with multi-line comment") {
            """
                //? if true {
                /*val one = 1
                val two = 2
                *///?}
            """.trimIndent() shouldAssembleTo """
                //? if true {
                val one = 1
                val two = 2
                //?}
            """.trimIndent()
        }

        test("disable scoped with single-line comment") {
            """
                //? if false {
                val one = 1
                //?}
            """.trimIndent() shouldAssembleTo """
                //? if false {
                //val one = 1
                //?}
            """.trimIndent()
        }

        test("enable scoped with single-line comment") {
            """
                //? if true {
                //val one = 1
                //?}
            """.trimIndent() shouldAssembleTo """
                //? if true {
                val one = 1
                //?}
            """.trimIndent()
        }

        test("disable line with one single-line comment") {
            """
                //? if false
                val one = 1
                val two = 2
            """.trimIndent() shouldAssembleTo """
                //? if false
                //val one = 1
                val two = 2
            """.trimIndent()
        }

        test("disable line with one multi-line comment") {
            """
                //? if false
                
                
                val one = 1
                val two = 2
            """.trimIndent() shouldAssembleTo """
                //? if false
                /*
                
                val one = 1*/
                val two = 2
            """.trimIndent()
        }

        test("enable line with multiple single-line comments") {
            """
                //? if true
                //
                //
                //val one = 1
            """.trimIndent() shouldAssembleTo """
                //? if true
                
                
                val one = 1
            """.trimIndent()
        }

        test("enable line with one multi-line comment") {
            """
                //? if true
                /*
                
                val one = 1*/
            """.trimIndent() shouldAssembleTo """
                //? if true
                
                
                val one = 1
            """.trimIndent()
        }

        test("disable lookup with multi-line comment") {
            """
                //? if false >> 'val th'
                val one = 1
                val two = 2
                val three = 3
            """.trimIndent() shouldAssembleTo """
                //? if false >> 'val th'
                /*val one = 1
                val two = 2
                */val three = 3
            """.trimIndent()
        }

        // Changed to capture the whole comment
        test("enable line captures whole comment") {
            """
                //? if true
                /*val one = 1
                val two = 2*/
            """.trimIndent() shouldAssembleTo """
                //? if true
                val one = 1
                val two = 2
            """.trimIndent()
        }

        test("disable lookup with single-line comment") {
            """
                //? if false >> 'val two'
                val one = 1
                val two = 2
            """.trimIndent() shouldAssembleTo """
                //? if false >> 'val two'
                //val one = 1
                val two = 2
            """.trimIndent()
        }

        test("enable lookup on one comment") {
            """
                //? if true >> 'val two'
                //val one = 1
                val two = 2
            """.trimIndent() shouldAssembleTo """
                //? if true >> 'val two'
                val one = 1
                val two = 2
            """.trimIndent()
        }

        // Changed to capture the whole comment
        test("enable lookup captures whole comment") {
            """
                //? if true >> 'val two'
                /*val one = 1
                val two = 2*/
            """.trimIndent() shouldAssembleTo """
                //? if true >> 'val two'
                val one = 1
                val two = 2
            """.trimIndent()
        }

        // Changed to capture the whole comment
        test("enable lookup captures whole line comment") {
            """
                //? if true >> 'val two'
                //val one = 1; val two = 2
            """.trimIndent() shouldAssembleTo """
                //? if true >> 'val two'
                val one = 1; val two = 2
            """.trimIndent()
        }

        test("keep single-line comment indent") {
            """
                val one = 1
                    //? if false
                        val two = 2
            """.trimIndent() shouldAssembleTo """
                val one = 1
                    //? if false
                        //val two = 2
            """.trimIndent()
        }

        test("capture nested statements in line scope") {
            """
                //? if false
                    //? if false
                        val one = 1
            """.trimIndent() shouldAssembleTo """
                //? if false
                    /*//? if false
                        //val one = 1
                */
            """.trimIndent()
        }

        test("capture extended statements in line scope") {
            """
                //? if false
                    //? if false
                    val one = 1

                    //? else
                    //val two = 2
            """.trimIndent() shouldAssembleTo """
                //? if false
                    /*//? if false
                    //val one = 1

                    //? else
                    val two = 2*/
            """.trimIndent()
        }
    }

    testSuite("condition branches") {
        test("retain state") {
            """
                //? if true
                val one = 1
                //? else
                //val one = 1.0
            """.trimIndent() shouldAssembleTo """
                //? if true
                val one = 1
                //? else
                //val one = 1.0
            """.trimIndent()
        }

        test("reformat state") {
            """
                //? if true
                val one = 1
                //? else
                /*val one = 1.0*/
            """.trimIndent() shouldAssembleTo """
                //? if true
                val one = 1
                //? else
                //val one = 1.0
            """.trimIndent()
        }

        test("flip state") {
            """
                //? if false
                val one = 1
                //? else
                //val one = 1.0
            """.trimIndent() shouldAssembleTo """
                //? if false
                //val one = 1
                //? else
                val one = 1.0
            """.trimIndent()
        }

        test("retain chain") {
            """
                //? if false
                //val one = 1
                //? elif true
                val one = 1F
                //? else
                //val one = 1.0
            """.trimIndent() shouldAssembleTo """
                //? if false
                //val one = 1
                //? elif true
                val one = 1F
                //? else
                //val one = 1.0
            """.trimIndent()
        }

        test("fall through chain") {
            """
                //? if true
                //val one = 1
                //? elif false
                val one = 1F
                //? else
                val one = 1.0
            """.trimIndent() shouldAssembleTo """
                //? if true
                val one = 1
                //? elif false
                //val one = 1F
                //? else
                //val one = 1.0
            """.trimIndent()
        }
    }

    // The [Kotlin] commenter doesn't need flattening, but the [Java] one is used here for testing
    testSuite("condition flattening") {
        test("don't apply in single-line") {
            """
                //? if false
                val one = 1 /* the one piece is real! */
            """.trimIndent() shouldAssembleTo """
                //? if false
                //val one = 1 /* the one piece is real! */
            """.trimIndent()
        }

        test("apply in multi-line") {
            """
                //? if false {
                /* what now? two peace? */
                val two = 2
                //?}
            """.trimIndent() shouldAssembleTo """
                //? if false {
                /*/^ what now? two peace? ^/
                val two = 2
                *///?}
            """.trimIndent()
        }

        test("flatten multiple block comments") {
            """
                //? if false {
                /* a */ x /* b */
                val two = 2
                //?}
            """.trimIndent() shouldAssembleTo """
                //? if false {
                /*/^ a ^/ x /^ b ^/
                val two = 2
                *///?}
            """.trimIndent()
        }

        test("restore superscript markers migrated from the old system") {
            // A comment already carrying `/^ ^/` placeholders (from a legacy Stonecutter version) is
            // restored to real block delimiters when its branch is enabled.
            """
                //? if true {
                /*/^ note ^/
                val two = 2
                *///?}
            """.trimIndent() shouldAssembleTo """
                //? if true {
                /* note */
                val two = 2
                //?}
            """.trimIndent()
        }

        test("markers inside strings are not distinguished") {
            // KNOWN LIMITATION: the scanner does not treat comment delimiters inside string literals
            // specially, so string-embedded markers are left un-flattened when the scope is disabled.
            """
                //? if false {
                val x = "/* hi */"
                val two = 2
                //?}
            """.trimIndent() shouldAssembleTo """
                //? if false {
                /*val x = "/* hi */"
                val two = 2
                *///?}
            """.trimIndent()
        }
    }

    testSuite("replacements") {
        test("global replacements apply to commented scopes") {
            val params = params { string("old", "new") }
            """
                //? if false
                //val x = "old"
            """.trimIndent().shouldAssembleTo(
                params, """
                //? if false
                //val x = "new"
            """.trimIndent()
            )
        }

        test("global replacement with disabled id") {
            val params = params { string("old", "new", id = "id") }
            """
                val x = "old"
                //~ id
                val y = "old"
            """.trimIndent().shouldAssembleTo(
                params, """
                val x = "old"
                //~ id
                val y = "new"
            """.trimIndent()
            )
        }

        test("global replacement with enabled id") {
            val params = params { string("old", "new", id = "id", enabled = true) }
            """
                val x = "old"
                //~ !id
                val y = "old"
            """.trimIndent().shouldAssembleTo(
                params, """
                val x = "new"
                //~ !id
                val y = "old"
            """.trimIndent()
            )
        }

        test("replacements don't apply to swap scopes") {
            val params = params { string("old", "new"); swaps["id"] = "val str = \"old\"" }
            """
                //$ id
                val str = "old"
            """.trimIndent().shouldAssembleTo(
                params, """
                //$ id
                val str = "old"
            """.trimIndent()
            )
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/40
        test("recognize existing target pattern") {
            val params = params {
                string("RenderType", "RenderTypes")
            }
            "RenderType - RenderTypes".shouldAssembleTo(params, "RenderTypes - RenderTypes")
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/43
        test("replace across comments") {
            val params = params {
                string("/*Map*/Codec", "MapCodec")
            }
            "My/*Map*/Codec".shouldAssembleTo(params, "MyMapCodec")
        }

        test("local replacement applies within its scope") {
            """
                //~ if true 'b.c' -> '2.3' {
                a.b.c.d.e
                //~}
            """.trimIndent() shouldAssembleTo """
                //~ if true 'b.c' -> '2.3' {
                a.2.3.d.e
                //~}
            """.trimIndent()
        }

        test("inactive local replacement leaves content untouched") {
            """
                //~ if false 'b.c' -> '2.3' {
                a.b.c.d.e
                //~}
            """.trimIndent() shouldAssembleTo """
                //~ if false 'b.c' -> '2.3' {
                a.b.c.d.e
                //~}
            """.trimIndent()
        }

        test("local and global replacements combine") {
            val params = params { string("d.e", "4.5") }
            """
                //~ if true 'b.c' -> '2.3' {
                a.b.c.d.e
                //~}
            """.trimIndent().shouldAssembleTo(
                params, """
                //~ if true 'b.c' -> '2.3' {
                a.2.3.4.5
                //~}
            """.trimIndent()
            )
        }

        test("named local replacement applies to the rest of the file") {
            """
                val pre = a.b
                //~ if true 'a.b' -> 'x.y' as ren
                val post1 = a.b
                val post2 = a.b
                val post3 = a.b
            """.trimIndent() shouldAssembleTo """
                val pre = a.b
                //~ if true 'a.b' -> 'x.y' as ren
                val post1 = x.y
                val post2 = x.y
                val post3 = x.y
            """.trimIndent()
        }

        test("unnamed local replacement") {
            """
                val pre = a.b
                //~ if true 'a.b' -> 'x.y' as _
                val post1 = a.b
                val post2 = a.b
                val post3 = a.b
            """.trimIndent() shouldAssembleTo """
                val pre = a.b
                //~ if true 'a.b' -> 'x.y' as _
                val post1 = x.y
                val post2 = x.y
                val post3 = x.y
            """.trimIndent()
        }

        test("escaped quote in replacement pattern") {
            """
                val pre = "a'b"
                //~ if true 'a\'b' -> 'x' as _
                val post = "a'b"
            """.trimIndent() shouldAssembleTo """
                val pre = "a'b"
                //~ if true 'a\'b' -> 'x' as _
                val post = "x"
            """.trimIndent()
        }

        test("replacement toggle enables and disables ids in one marker") {
            val params = params {
                string("olda", "newa", id = "a")
                string("oldb", "newb", id = "b", enabled = true)
            }
            """
                val a1 = "olda"
                val b1 = "oldb"
                //~ a !b
                val a2 = "olda"
                val b2 = "oldb"
            """.trimIndent().shouldAssembleTo(
                params, """
                val a1 = "olda"
                val b1 = "newb"
                //~ a !b
                val a2 = "newa"
                val b2 = "oldb"
            """.trimIndent()
            )
        }
    }

    testSuite("swaps") {
        // https://codeberg.org/stonecutter/stonecutter/issues/32
        test("substitute empty scope") {
            val params = params { swaps["id"] = "inserted" }
            "/*$ id {*///$}".shouldAssembleTo(params, "/*$ id {*/inserted//$}")
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/34
        test("parameter overflow bug") {
            val params = params { swaps["open_url"] = "ClickEvent(ClickEvent.Action.OPEN_URL, $1)" }
            "/*$ open_url '\"example\"' {*/ClickEvent.OpenUrl(URI.create(\"example\"))/*$}*/"
                .shouldAssembleTo(params, "/*$ open_url '\"example\"' {*/ClickEvent(ClickEvent.Action.OPEN_URL, \"example\")/*$}*/")
        }

        test("substitution is re-indented to the scope") {
            val params = params {
                swaps["id"] = "  val one = 1\n  val two = 2"
            }
            """
                run {
                    //$ id {
                    val three = 3
                    //$}
                }
            """.trimIndent().shouldAssembleTo(
                params, """
                run {
                    //$ id {
                    val one = 1
                    val two = 2
                    //$}
                }
            """.trimIndent()
            )
        }

        test("mid-line swap leaves the first line un-indented") {
            val params = params {
                swaps["id"] = "val one = 1\n  val two = 2"
            }
            """
                run {
                    /*$ id {*/val one = 1
                    val three = 3
                    //$}
                }
            """.trimIndent().shouldAssembleTo(
                params, """
                run {
                    /*$ id {*/val one = 1
                    val two = 2
                    //$}
                }
            """.trimIndent()
            )
        }

        test("swap template expands placeholders") {
            val params = params { swaps["id"] = "\$1 and \$2" }
            "/*\$ id 'X' 'Y' {*/old/*\$}*/".shouldAssembleTo(params, "/*\$ id 'X' 'Y' {*/X and Y/*\$}*/")
        }

        test("local swap picks the branch matching its condition") {
            val content = """
                //$ if param 'method1()' else 'method2()'
                    ???
            """.trimIndent()
            content.shouldAssembleTo(
                params { constants["param"] = true }, """
                //$ if param 'method1()' else 'method2()'
                    method1()
            """.trimIndent()
            )
            content.shouldAssembleTo(
                params { constants["param"] = false }, """
                //$ if param 'method1()' else 'method2()'
                    method2()
            """.trimIndent()
            )
        }
    }

    // Test for an old reported bug, where the [NOT] operator was capturing the entire [OR]
    test("operator associativity") {
        val params = params {
            constants["neoforge"] = false
            targets[""] = SemanticVersion(1, 20, 1)
        }
        val content = """
            //? !neoforge || <1.20.5
            val ok = true
        """.trimIndent()

        content.shouldAssembleTo(params, content)
    }

    // A line condition whose disabled body is a multi-line `/* ... */` block (produced by disabling a nested
    // condition) must capture the whole block, both with and without replacements configured. Splitting the
    // block orphaned its remainder as a sibling on a different document, which crashed the replacement pass.
    test("nested condition in block comment scope") {
        val content = """
            fun test() {
                //? if >26.2
                /*//? if >26.1
                println(1)
                //? else
                //println(2)
            */}
        """.trimIndent()

        content.shouldAssembleTo(
            params { targets[""] = SemanticVersion(26, 3) }, """
            fun test() {
                //? if >26.2
                //? if >26.1
                println(1)
                //? else
                //println(2)
            }
        """.trimIndent()
        )

        content.shouldAssembleTo(
            params {
                targets[""] = SemanticVersion(26, 3)
                string("nonexistent", "whatever", enabled = true)
            }, """
            fun test() {
                //? if >26.2
                //? if >26.1
                println(1)
                //? else
                //println(2)
            }
        """.trimIndent()
        )
    }

    test("nested version condition inside a constant branch") {
        val content = """
            //? if neoforge {
            import net.neoforged.bus.api.SubscribeEvent;
            import net.neoforged.neoforge.client.event.RenderFrameEvent;
            //? if >=1.21.6
            import net.neoforged.neoforge.client.event.ViewportEvent;
            //? } else {
            /*import net.minecraftforge.event.TickEvent;
            import net.minecraftforge.eventbus.api.SubscribeEvent;
            *///? }
        """.trimIndent()

        content.shouldAssembleTo(params {
            constants["neoforge"] = true
            targets[""] = SemanticVersion(1, 21, 6)
        }, content)

        content.shouldAssembleTo(
            params {
                constants["neoforge"] = true
                targets[""] = SemanticVersion(1, 21, 5)
            }, """
            //? if neoforge {
            import net.neoforged.bus.api.SubscribeEvent;
            import net.neoforged.neoforge.client.event.RenderFrameEvent;
            //? if >=1.21.6
            //import net.neoforged.neoforge.client.event.ViewportEvent;
            //? } else {
            /*import net.minecraftforge.event.TickEvent;
            import net.minecraftforge.eventbus.api.SubscribeEvent;
            *///? }
        """.trimIndent()
        )

        content.shouldAssembleTo(
            params {
                constants["neoforge"] = false
                targets[""] = SemanticVersion(1, 21, 6)
            }, """
            //? if neoforge {
            /*import net.neoforged.bus.api.SubscribeEvent;
            import net.neoforged.neoforge.client.event.RenderFrameEvent;
            //? if >=1.21.6
            import net.neoforged.neoforge.client.event.ViewportEvent;
            *///? } else {
            import net.minecraftforge.event.TickEvent;
            import net.minecraftforge.eventbus.api.SubscribeEvent;
            //? }
        """.trimIndent()
        )
    }
}

context(scope: Test.ExecutionScope)
private infix fun @receiver:Language("kotlin") String.shouldAssembleTo(@Language("kotlin") expected: String): Unit =
    shouldAssembleTo(params(), expected)

context(scope: Test.ExecutionScope)
private fun @receiver:Language("kotlin") String.shouldAssembleTo(params: StitcherParameters, @Language("kotlin") expected: String) {
    scope.assemble(this, params) shouldBe expected
}
