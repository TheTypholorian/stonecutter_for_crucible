@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.Test
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.disable
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.semver.data.SemanticVersion
import dev.kikugie.stitcher.StitcherParameters
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import matcher.assemble
import matcher.params
import org.intellij.lang.annotations.Language

/**
 * End-to-end transformation cases ported from the pre-rework Kotest suites
 * (`SyntaxTest`, `TransformTest`, `v2.BlockTransformerTest`, `v2.FileAstBuilderTest`).
 *
 * Each case feeds a snippet through [assemble] and compares the rendered output, mirroring the
 * old `process(content) shouldBe expected` checks. `dependencies` became [params]`.targets` and the
 * `replacements.string/regex` helpers moved to top-level [params] builder functions.
 *
 * Cases tagged `testConfig = TestConfig.disable()` render differently under the current rework than
 * the pre-rework engine did. Each keeps the legacy `shouldAssembleTo` expectation and a `FIXME`
 * noting the current output, so the divergence is documented and the case is ready to re-enable once
 * the behaviour is settled (matching the old suite's own `.config(enabled = false)` marker).
 */
val TransformTest by testSuite {
    testSuite("lookup") {
        test("disable non-capturing") {
            """
                //? if false >> '='
                int one = 1;
                int two = 2;
            """.trimIndent() shouldAssembleTo """
                //? if false >> '='
                /*int one */= 1;
                int two = 2;
            """.trimIndent()
        }

        test("enable non-capturing") {
            """
                //? if true >> '='
                /*int one */= 1;
                int two = 2;
            """.trimIndent() shouldAssembleTo """
                //? if true >> '='
                int one = 1;
                int two = 2;
            """.trimIndent()
        }

        test("disable capturing") {
            """
                //? if false >>+ '='
                int one = 1;
                int two = 2;
            """.trimIndent() shouldAssembleTo """
                //? if false >>+ '='
                /*int one =*/ 1;
                int two = 2;
            """.trimIndent()
        }

        test("enable capturing") {
            """
                //? if true >>+ '='
                /*int one =*/ 1;
                int two = 2;
            """.trimIndent() shouldAssembleTo """
                //? if true >>+ '='
                int one = 1;
                int two = 2;
            """.trimIndent()
        }

        test("search comments") {
            """
                //? if true >>+ '2;'
                //int one = 1;
                //int two = 2;
                // unused
            """.trimIndent() shouldAssembleTo """
                //? if true >>+ '2;'
                int one = 1;
                int two = 2;
                // unused
            """.trimIndent()
        }

        // FIXME(rework): capturing lookup no longer splits an existing comment — output stays fully enabled.
        test("split comment", testConfig = TestConfig.disable()) {
            """
                //? if true >>+ 'two ='
                //int one = 1;
                //int two = 2;
                // unused
            """.trimIndent() shouldAssembleTo """
                //? if true >>+ 'two ='
                int one = 1;
                int two = /*2;*/
                // unused
            """.trimIndent()
        }

        // FIXME(rework): default `>>` word lookup inside a comment is a no-op — output stays `... ...`.
        test("locate word break in comments", testConfig = TestConfig.disable()) {
            """
                //? if true >>
                //... ...
            """.trimIndent() shouldAssembleTo """
                //? if true >>
                .../* ...*/
            """.trimIndent()
        }

        // FIXME(rework): string lookup that lands inside a comment is a no-op — output stays `... //.;.`.
        test("locate custom lookup string across comments", testConfig = TestConfig.disable()) {
            """
                //? if true >> ';'
                ... //.;.
            """.trimIndent() shouldAssembleTo """
                //? if true >> ';'
                ... ./*;.*/
            """.trimIndent()
        }
    }

    testSuite("extended line") {
        // FIXME(rework): extended line scope no longer re-comments the second statement — `int two = 2;` stays active.
        test("search comments", testConfig = TestConfig.disable()) {
            """
                //? if true
                /*    */
                //
                //int one = 1;
                //int two = 2;
            """.trimIndent() shouldAssembleTo """
                //? if true


                int one = 1;
                //int two = 2;
            """.trimIndent()
        }

        // FIXME(rework): extended line scope no longer splits the trailing multiline comment — `int two = 2;` stays enabled.
        test("split comment", testConfig = TestConfig.disable()) {
            """
                //? if true
                /*

                int one = 1;
                int two = 2;
                *///unused
            """.trimIndent() shouldAssembleTo """
                //? if true


                int one = 1;
                /*int two = 2;
                *///unused
            """.trimIndent()
        }

        // FIXME(rework): as above, the trailing multiline comment is not split — `int two = 2;` stays enabled.
        test("split comment indent", testConfig = TestConfig.disable()) {
            """
                hello();
                    //? if true
                    /*int one = 1;
                    int two = 2;
                    */
            """.trimIndent() shouldAssembleTo """
                hello();
                    //? if true
                    int one = 1;
                    /*int two = 2;
                    */
            """.trimIndent()
        }

        test("excluded line break") {
            """
                //? if true
                /*int one = 1;*/
                //unused
            """.trimIndent() shouldAssembleTo """
                //? if true
                int one = 1;
                //unused
            """.trimIndent()
        }
    }

    testSuite("replacements") {
        test("simple string") {
            "Hello World!".shouldAssembleTo("Hello Lace!", params { string("World", "Lace") })
        }

        test("contained string") {
            "a.b.c".shouldAssembleTo("a.b.c", params { string("b", "a.b.c") })
        }

        test("longest string") {
            "a.b.c".shouldAssembleTo(
                "e.c",
                params {
                    string("a", "d")
                    string("a.b", "e")
                }
            )
        }

        test("active identifier") {
            """
                //~ example
                a.b.c
            """.trimIndent().shouldAssembleTo(
                """
                    //~ example
                    a.d.c
                """.trimIndent(),
                params { string("b", "d", "example") }
            )
        }

        test("inactive identifier") {
            "a.b.c".shouldAssembleTo("a.b.c", params { string("b", "d", "example") })
        }

        // Matches inside comments should be replaced
        test("commented values") {
            """
                a.b.c
                // a.b.c
            """.trimIndent().shouldAssembleTo(
                """
                    a.d.c
                    // a.d.c
                """.trimIndent(),
                params { string("b", "d") }
            )
        }

        // Matches in what is about to be processed should be replaced as well
        test("commented out values") {
            """
                //? if it
                a.b.c
            """.trimIndent().shouldAssembleTo(
                """
                    //? if it
                    //a.d.c
                """.trimIndent(),
                params {
                    constants["it"] = false
                    string("b", "d")
                }
            )
        }

        // Matches in code that was uncommented should be replaced
        test("uncommented values") {
            """
                //? if it
                //a.b.c
            """.trimIndent().shouldAssembleTo(
                """
                    //? if it
                    a.d.c
                """.trimIndent(),
                params {
                    constants["it"] = true
                    string("b", "d")
                }
            )
        }

        test("string-regex overlap") {
            val content = "a.b.c.d"
            val expected = "a.2.c.d"
            content.shouldAssembleTo(
                expected,
                params {
                    string("a.b.c", "a.2.c")
                    regex("b\\.c\\.d", "!!!")
                }
            )
            content.shouldAssembleTo(
                expected,
                params {
                    string("b.c.d", "!!!")
                    regex("a\\.b\\.c", "a.2.c")
                }
            )
        }

        test("toggle multiple ids") {
            """
                a.b.c.d.e
                //~ !ab de
                a.b.c.d.e
            """.trimIndent().shouldAssembleTo(
                """
                    1.2.c.d.e
                    //~ !ab de
                    a.b.c.4.5
                """.trimIndent(),
                params {
                    string("a.b", "1.2", id = "ab", enabled = true)
                    string("d.e", "4.5", id = "de")
                }
            )
        }

        test("ignore subsequent toggles") {
            """
                //~ !my_id
                //~ !my_id
                a.b.c
            """.trimIndent().shouldAssembleTo(
                """
                    //~ !my_id
                    //~ !my_id
                    a.b.c
                """.trimIndent(),
                params { string("a.b", "1.2", id = "my_id") }
            )
        }

        test("basic local replacement") {
            """
                a.b.c.d.e
                //~ if true 'b.c' -> '2.3' {
                a.b.c.d.e
                a.b.c.d.e
                //~}
            """.trimIndent() shouldAssembleTo """
                a.b.c.d.e
                //~ if true 'b.c' -> '2.3' {
                a.2.3.d.e
                a.2.3.d.e
                //~}
            """.trimIndent()
        }

        test("nested local replacement") {
            """
                a.b.c.d.e
                //~ if true 'b.c' -> '2.3' {
                a.b.c.d.e
                //~ if true 'd.e' -> '4.5'
                a.b.c.d.e
                a.b.c.d.e
                //~}
            """.trimIndent() shouldAssembleTo """
                a.b.c.d.e
                //~ if true 'b.c' -> '2.3' {
                a.2.3.d.e
                //~ if true 'd.e' -> '4.5'
                a.2.3.4.5
                a.2.3.d.e
                //~}
            """.trimIndent()
        }

        test("regex groups") {
            val content = """
                EntityType.RABBIT
                EntityType.RABBIT
            """.trimIndent()
            val expected = """
                EntityTypes.RABBIT
                EntityTypes.RABBIT
            """.trimIndent()
            content.shouldAssembleTo(expected, params { regex("EntityType\\.([A-Z_]+)", "EntityTypes.\$1") })
            expected.shouldAssembleTo(content, params { regex("EntityTypes.([A-Z_]+)", "EntityType.\$1") })
        }

        test("weird replacement combo") {
            """
                //~ if true 'gui.Hud' -> 'gui.Gui'
                import net.gui.Hud
                import net.gui.GuiGraphicsExtractor
            """.trimIndent().shouldAssembleTo(
                """
                    //~ if true 'gui.Hud' -> 'gui.Gui'
                    import net.gui.Gui
                    import net.gui.GuiGraphics
                """.trimIndent(),
                params {
                    string("GuiGraphicsExtractor", "GuiGraphics")
                    constants["true"] = true
                }
            )
        }
    }

    testSuite("predicate conditions") {
        test("unary negation") {
            """
                //? if !true
                ...
            """.trimIndent() shouldAssembleTo """
                //? if !true
                //...
            """.trimIndent()
        }

        test("boolean or") {
            """
                //? if false || true
                //...
            """.trimIndent() shouldAssembleTo """
                //? if false || true
                ...
            """.trimIndent()
        }

        test("boolean and") {
            """
                //? if false && true
                ...
            """.trimIndent() shouldAssembleTo """
                //? if false && true
                //...
            """.trimIndent()
        }

        test("single predicate") {
            """
                //? if >1
                ...
            """.trimIndent().shouldAssembleTo(
                """
                    //? if >1
                    //...
                """.trimIndent(),
                params { targets[""] = SemanticVersion(1) }
            )
        }

        test("multiple predicates") {
            val content = """
                //? if >1 <3
                //...
            """.trimIndent()
            content.shouldAssembleTo(content, params { targets[""] = SemanticVersion(3) })
        }

        test("named predicates") {
            val content = """
                //? if id: >1 <3
                //...
            """.trimIndent()
            content.shouldAssembleTo(content, params { targets["id"] = SemanticVersion(3) })
        }

        test("predicate operator precedence") {
            """
                //? if !=1 <3
                //...
            """.trimIndent().shouldAssembleTo(
                """
                    //? if !=1 <3
                    ...
                """.trimIndent(),
                params { targets[""] = SemanticVersion(2) }
            )
        }

        test("pre-release wildcard admits a pre-release of its own line") {
            val content = """
                //? if >=1.21-*
                ...
            """.trimIndent()
            content.shouldAssembleTo(content, params { targets[""] = SemanticVersion(1, 21, preRelease = "alpha.1") })
        }

        test("a bare bound excludes the pre-releases below it") {
            """
                //? if >=1.21
                ...
            """.trimIndent().shouldAssembleTo(
                """
                    //? if >=1.21
                    //...
                """.trimIndent(),
                params { targets[""] = SemanticVersion(1, 21, preRelease = "alpha.1") }
            )
        }

        test("pre-release wildcard bounds from below") {
            """
                //? if <1.21-*
                ...
            """.trimIndent().shouldAssembleTo(
                """
                    //? if <1.21-*
                    //...
                """.trimIndent(),
                params { targets[""] = SemanticVersion(1, 21, preRelease = "alpha.1") }
            )
        }

        test("pre-release wildcard as a range bound") {
            val content = """
                //? if >=1.20-* <1.21-*
                ...
            """.trimIndent()
            content.shouldAssembleTo(content, params { targets[""] = SemanticVersion(1, 20, preRelease = "beta") })
        }

        test("pre-release wildcard under the same-minor operator") {
            val content = """
                //? if ~1.21-*
                ...
            """.trimIndent()
            content.shouldAssembleTo(content, params { targets[""] = SemanticVersion(1, 21, preRelease = "alpha") })
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/31
        test("spaced string lookup bug") {
            "/*? if <1.21.9 >>*/ /*,0*/ )".shouldAssembleTo(
                "/*? if <1.21.9 >>*/ ,0 )",
                params { targets[""] = SemanticVersion(1, 21, 8) }
            )
        }
    }

    testSuite("else chains") {
        // FIXME(rework): a single-line closed scope is now line-commented (`//...`) instead of block-commented (`/*...*/`).
        test("switch block-block", testConfig = TestConfig.disable()) {
            """
                //? if false {
                ...
                //?} else {
                /*...
                *///?}
            """.trimIndent() shouldAssembleTo """
                //? if false {
                /*...
                *///?} else {
                ...
                //?}
            """.trimIndent()
        }

        // FIXME(rework): as above, the single-line closed branch is line-commented instead of block-commented.
        test("switch block-line", testConfig = TestConfig.disable()) {
            """
                //? if false {
                ...
                //?} else
                //...
            """.trimIndent() shouldAssembleTo """
                //? if false {
                /*...
                *///?} else
                ...
            """.trimIndent()
        }

        test("switch line-line") {
            """
                //? if false
                ...
                //? else
                //...
            """.trimIndent() shouldAssembleTo """
                //? if false
                //...
                //? else
                ...
            """.trimIndent()
        }

        test("switch line-block") {
            """
                //? if false
                ...
                //? else {
                /*...
                *///?}
            """.trimIndent() shouldAssembleTo """
                //? if false
                //...
                //? else {
                ...
                //?}
            """.trimIndent()
        }
    }

    testSuite("unfinished line scopes") {

        // FIXME(rework): closing `*/` is placed before the trailing indent (`*/    ...`) instead of after it (`    */...`).
        test("capture closed statements", testConfig = TestConfig.disable()) {
            """
                //? if false
                    //? if true {
                    ...
                    //?}
                    ...
            """.trimIndent() shouldAssembleTo """
                //? if false
                    /*//? if true {
                    ...
                    //?}
                    */...
            """.trimIndent()
        }
    }

    testSuite("swaps") {
        test("oob placeholder") {
            shouldThrowAny {
                assemble("/*\$ id 'hello' {*/.../*\$}*/", params { swaps["id"] = "\$1 \$2" })
            }
        }

        test("basic local swap") {
            val content = """
                //$ if param 'method1()' else 'method2()'
                    ???
            """.trimIndent()
            content.shouldAssembleTo(
                """
                    //$ if param 'method1()' else 'method2()'
                        method1()
                """.trimIndent(),
                params { constants["param"] = true }
            )
            content.shouldAssembleTo(
                """
                    //$ if param 'method1()' else 'method2()'
                        method2()
                """.trimIndent(),
                params { constants["param"] = false }
            )
        }

        test("horrible local swap") {
            val content = """
                //$ if param1 'method1()' elif param2 'method2()' else 'method3()'
                    ???
            """.trimIndent()
            content.shouldAssembleTo(
                """
                    //$ if param1 'method1()' elif param2 'method2()' else 'method3()'
                        method1()
                """.trimIndent(),
                params { constants["param1"] = true; constants["param2"] = true }
            )
            content.shouldAssembleTo(
                """
                    //$ if param1 'method1()' elif param2 'method2()' else 'method3()'
                        method2()
                """.trimIndent(),
                params { constants["param1"] = false; constants["param2"] = true }
            )
            content.shouldAssembleTo(
                """
                    //$ if param1 'method1()' elif param2 'method2()' else 'method3()'
                        method3()
                """.trimIndent(),
                params { constants["param1"] = false; constants["param2"] = false }
            )
        }
    }

    testSuite("deep nesting") {
        test("nested placeholders stay flat", testConfig = TestConfig.disable()) {
            """
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
            """.trimIndent().shouldAssembleTo(
                """
                    /*? if false {*/
                        /*...
                        /^? if false {^/
                            /^...
                            /^? if false {^/
                                /^...
                                /^? if false {^/
                                    /^...
                                    /^? if false {^/
                                        /^...
                                        /^? if false {^/
                                            /^...
                                            /^? if false {^/
                                                /^...
                                                /^? if false {^/
                                                    /^...
                                                    /^? if false {^/
                                                        /^...
                                                        /^? if false {^/
                                                            /^...
                                                            /^? if false {^/
                                                                /^...
                                                            ^//^?}^/
                                                        ^//^?}^/
                                                    ^//^?}^/
                                                ^//^?}^/
                                            ^//^?}^/
                                        ^//^?}^/
                                    ^//^?}^/
                                ^//^?}^/
                            ^//^?}^/
                        ^//^?}^/
                    *//*?}*/
                """.trimIndent(),
                params { constants["false"] = false }
            )
        }
    }

    testSuite("escaped comments") {
        // https://codeberg.org/stonecutter/stonecutter/issues/72
        test("odd quote count in a nested comment") {
            val enabled = """
                //? if <=1.21.11 {
                void method() {
                	/* it's */
                }
                //?}
            """.trimIndent().prependIndent("\t")
            val disabled = """
                //? if <=1.21.11 {
                /*void method() {
                	/^ it's ^/
                }
                *///?}
            """.trimIndent().prependIndent("\t")
            enabled.shouldAssembleTo(disabled, params { targets[""] = SemanticVersion(1, 22, 0) })
            disabled.shouldAssembleTo(enabled, params { targets[""] = SemanticVersion(1, 21, 11) })
        }

        test("odd quote count in a multi-line nested comment") {
            val enabled = """
                //? if <=1.21.11 {
                void method() {
                	/* it's
                	 */
                }
                //?}
            """.trimIndent().prependIndent("\t")
            val disabled = """
                //? if <=1.21.11 {
                /*void method() {
                	/^ it's
                	 ^/
                }
                *///?}
            """.trimIndent().prependIndent("\t")
            enabled.shouldAssembleTo(disabled, params { targets[""] = SemanticVersion(1, 22, 0) })
            disabled.shouldAssembleTo(enabled, params { targets[""] = SemanticVersion(1, 21, 11) })
        }

        test("escape markers in a string under nested conditions") {
            val enabled = """
                //? if a {
                void method() {
                    //? if b {
                    String s = "^/"; // it's
                    foo();
                    //?}
                }
                //?}
            """.trimIndent()
            val disabled = """
                //? if a {
                /*void method() {
                    //? if b {
                    /^String s = "^/"; // it's
                    foo();
                    ^///?}
                }
                *///?}
            """.trimIndent()
            enabled.shouldAssembleTo(disabled, params { constants["a"] = false; constants["b"] = false })
            disabled.shouldAssembleTo(enabled, params { constants["a"] = true; constants["b"] = true })
        }

        // https://codeberg.org/stonecutter/stonecutter/issues/74
        test("apostrophe in a javadoc followed by quoted strings") {
            val enabled = """
                //? if <26.1 {
                package a;

                /**
                 * Implementation using NBT's.
                 *
                 * @param tag
                 */
                public record Impl(Tag tag) {
                    public Result<String> getString(String key) {
                        return Result.failure("Key '" + key + "' is not a string.");
                    }
                }
                //?}
            """.trimIndent()
            val disabled = """
                //? if <26.1 {
                /*package a;

                /^*
                 * Implementation using NBT's.
                 *
                 * @param tag
                 ^/
                public record Impl(Tag tag) {
                    public Result<String> getString(String key) {
                        return Result.failure("Key '" + key + "' is not a string.");
                    }
                }
                *///?}
            """.trimIndent()
            enabled.shouldAssembleTo(disabled, params { targets[""] = SemanticVersion(26, 1, 0) })
            disabled.shouldAssembleTo(enabled, params { targets[""] = SemanticVersion(1, 20, 1) })
        }
    }
}

context(scope: Test.ExecutionScope)
private infix fun String.shouldAssembleTo(expected: String) {
    scope.assemble(this) shouldBe expected
}

context(scope: Test.ExecutionScope)
private fun String.shouldAssembleTo(expected: String, params: StitcherParameters) {
    scope.assemble(this, params) shouldBe expected
}
