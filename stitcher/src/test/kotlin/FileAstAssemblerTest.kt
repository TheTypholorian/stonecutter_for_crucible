@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.token.type.RootScopeState
import io.kotest.matchers.shouldBe
import matcher.Commenter
import matcher.Scanner
import matcher.Uncommenter
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

        test("disable multi-line with one single-line comment") {
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
                        //val one = 1*/
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

        test("replacement toggle switches every replacement sharing the id") {
            val params = params {
                string("olda", "newa", id = "feature", enabled = true)
                string("oldb", "newb", id = "feature")
            }
            """
                val a1 = "olda"
                val b1 = "oldb"
                //~ feature
                val a2 = "olda"
                val b2 = "oldb"
                //~ !feature
                val a3 = "olda"
                val b3 = "oldb"
            """.trimIndent().shouldAssembleTo(
                params, """
                val a1 = "newa"
                val b1 = "oldb"
                //~ feature
                val a2 = "newa"
                val b2 = "newb"
                //~ !feature
                val a3 = "olda"
                val b3 = "oldb"
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

        // A matched capturing lookup ends the scope just after the match.
        test("swap lookup ends at its match") {
            "println(/*$ sw >>+ '!'*/\n\t\"Hello Lace!\"\n);\nafter();".shouldAssembleTo(
                params { swaps["sw"] = "SWAPPED" },
                "println(/*$ sw >>+ '!'*/\n\tSWAPPED\"\n);\nafter();"
            )
        }

        test("swap lookup without a match preserves the rest of the file") {
            "println(/*$ sw >>+ '!'*/\n\tone();\n\ttwo();\n\tthree();".shouldAssembleTo(
                params { swaps["sw"] = "SWAPPED" },
                "println(/*$ sw >>+ '!'*/\n\tone();\n\ttwo();\n\tthree();"
            )
        }

        test("swap lookup matching at its start inserts before the match") {
            "/*$ sw >> 'void'*/void run();".shouldAssembleTo(
                params { swaps["sw"] = "static " },
                "/*$ sw >> 'void'*/static void run();"
            )
        }

        test("swap lookup matching at its start removes the text before the match") {
            "/*$ sw >> 'void'*/static void run();".shouldAssembleTo(
                params { swaps["sw"] = "" },
                "/*$ sw >> 'void'*/void run();"
            )
        }

        test("swap lookup without a match before the next marker preserves the text") {
            "a(/*$ sw >>+ '!'*/one();\n//? if true\ntwo();".shouldAssembleTo(
                params { swaps["sw"] = "SWAPPED" },
                "a(/*$ sw >>+ '!'*/one();\n//? if true\ntwo();"
            )
        }

        test("swap template expands placeholders") {
            val params = params { swaps["id"] = $$"$1 and $2" }
            "/*$ id 'X' 'Y' {*/old/*$}*/".shouldAssembleTo(params, "/*$ id 'X' 'Y' {*/X and Y/*$}*/")
        }

        test("swap template expands the braced placeholder form") {
            val params = params { swaps["id"] = $$"${1} and ${2}" }
            "/*$ id 'X' 'Y' {*/old/*$}*/".shouldAssembleTo(params, "/*$ id 'X' 'Y' {*/X and Y/*$}*/")
        }

        test("swap template repeats a placeholder") {
            val params = params { swaps["id"] = $$"$1 + $1" }
            "/*$ id 'X' {*/old/*$}*/".shouldAssembleTo(params, "/*$ id 'X' {*/X + X/*$}*/")
        }

        test("swap template expands placeholders at both edges") {
            val params = params { swaps["id"] = $$"$1$2" }
            "/*$ id 'X' 'Y' {*/old/*$}*/".shouldAssembleTo(params, "/*$ id 'X' 'Y' {*/XY/*$}*/")
        }

        // Placements are located in the template, so a `$n` arriving from an argument is never rescanned.
        test("swap argument containing a placeholder is not re-expanded") {
            val params = params { swaps["id"] = $$"$1" }
            "/*$ id '$2' {*/old/*$}*/".shouldAssembleTo(params, "/*$ id '$2' {*/$2/*$}*/")
        }

        // A template with no arguments keeps `$n` literal, so a template may hold a real dollar amount.
        test("swap template keeps placeholders literal without arguments") {
            val params = params { swaps["id"] = $$"cost = $1" }
            "/*$ id {*/old/*$}*/".shouldAssembleTo(params, $$"/*$ id {*/cost = $1/*$}*/")
        }

        // `SwapTemplateLexer` accepts any digit count; one too large for an Int is text, not a reference.
        test("swap template leaves an oversized placeholder number alone") {
            val params = params { swaps["id"] = $$"$99999999999999 $1" }
            "/*$ id 'X' {*/old/*$}*/".shouldAssembleTo(params, $$"/*$ id 'X' {*/$99999999999999 X/*$}*/")
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

    testSuite("root scope") {
        test("disable file") {
            """
                package a;

                class A {}
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.DISABLED }, """
                /*!stonecutter-disabled
                package a;

                class A {}*/
            """.trimIndent()
            )
        }

        test("enable file") {
            """
                /*!stonecutter-disabled
                package a;

                class A {}*/
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.ENABLED }, """
                package a;

                class A {}
            """.trimIndent()
            )
        }

        test("enable marked file with any state") {
            """
                /*!stonecutter-disabled
                package a;

                class A {}*/
            """.trimIndent() shouldAssembleTo """
                package a;

                class A {}
            """.trimIndent()
        }

        test("keep disabled file disabled") {
            """
                /*!stonecutter-disabled
                package a;

                class A {}*/
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.DISABLED }, """
                /*!stonecutter-disabled
                package a;

                class A {}*/
            """.trimIndent()
            )
        }

        test("keep enabled file enabled") {
            """
                package a;

                class A {}
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.ENABLED }, """
                package a;

                class A {}
            """.trimIndent()
            )
        }

        for (state in listOf(RootScopeState.ANY, RootScopeState.ENABLED)) test("keep unmarked comment with ${state.name.lowercase()} state") {
            """
                /*package a;

                class A {}*/
            """.trimIndent().shouldAssembleTo(
                params { root = state }, """
                /*package a;

                class A {}*/
            """.trimIndent()
            )
        }

        test("disable unmarked comment") {
            """
                /*package a;

                class A {}*/
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.DISABLED }, """
                /*!stonecutter-disabled
                /^package a;

                class A {}^/*/
            """.trimIndent()
            )
        }

        test("disable single-line file") {
            "val one = 1".shouldAssembleTo(
                params { root = RootScopeState.DISABLED }, """
                /*!stonecutter-disabled
                val one = 1*/
            """.trimIndent()
            )
        }

        test("keep blank file") {
            "\n\n".shouldAssembleTo(params { root = RootScopeState.DISABLED }, "\n\n")
        }

        test("disable file with leading whitespace") {
            """


                    val one = 1
                val two = 2

            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.DISABLED }, """


                /*!stonecutter-disabled
                    val one = 1
                val two = 2
                */
            """.trimIndent()
            )
        }

        test("enable file with leading whitespace") {
            """


                /*!stonecutter-disabled
                    val one = 1
                val two = 2
                */
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.ENABLED }, """


                    val one = 1
                val two = 2

            """.trimIndent()
            )
        }

        test("enable file with indented marker") {
            """

                  /*!stonecutter-disabled
                val one = 1
                val two = 2*/
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.ENABLED }, """

                val one = 1
                val two = 2
            """.trimIndent()
            )
        }

        test("disable file with disabled condition") {
            """
                //? if false {
                val one = 1
                val two = 2
                //?}
                val three = 3
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.DISABLED }, """
                /*!stonecutter-disabled
                //? if false {
                /^val one = 1
                val two = 2
                ^///?}
                val three = 3*/
            """.trimIndent()
            )
        }

        test("enable file with disabled condition") {
            """
                /*!stonecutter-disabled
                //? if false {
                /^val one = 1
                val two = 2
                ^///?}
                val three = 3*/
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.ENABLED }, """
                //? if false {
                /*val one = 1
                val two = 2
                *///?}
                val three = 3
            """.trimIndent()
            )
        }

        test("enable file with enabled condition") {
            """
                /*!stonecutter-disabled
                //? if true {
                /^val one = 1
                val two = 2
                ^///?}
                val three = 3*/
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.ENABLED }, """
                //? if true {
                val one = 1
                val two = 2
                //?}
                val three = 3
            """.trimIndent()
            )
        }

        test("disable hash file") {
            """
                key = 1
                other = 2
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.DISABLED; scanner = Scanner.Hash; commenter = Commenter.Hash; uncommenter = Uncommenter.Lines }, """
                #!stonecutter-disabled
                #key = 1
                #other = 2
            """.trimIndent()
            )
        }

        test("enable hash file") {
            """
                #!stonecutter-disabled
                #key = 1
                #other = 2
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.ENABLED; scanner = Scanner.Hash; commenter = Commenter.Hash; uncommenter = Uncommenter.Lines }, """
                key = 1
                other = 2
            """.trimIndent()
            )
        }

        test("disable xml file") {
            """
                <item id="a"/>
                <item id="b"/>
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.DISABLED; scanner = Scanner.Xml; commenter = Commenter.Xml; uncommenter = Uncommenter.Xml }, """
                <!--!stonecutter-disabled
                <item id="a"/>
                <item id="b"/>-->
            """.trimIndent()
            )
        }

        test("enable xml file") {
            """
                <!--!stonecutter-disabled
                <item id="a"/>
                <item id="b"/>-->
            """.trimIndent().shouldAssembleTo(
                params { root = RootScopeState.ENABLED; scanner = Scanner.Xml; commenter = Commenter.Xml; uncommenter = Uncommenter.Xml }, """
                <item id="a"/>
                <item id="b"/>
            """.trimIndent()
            )
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
