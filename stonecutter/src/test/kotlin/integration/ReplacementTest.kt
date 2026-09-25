@file:Suppress("PublicApiImplicitType")

package integration

import de.infix.testBalloon.framework.core.testSuite
import integrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import read
import runGradle
import tempDirFixture

val ReplacementTest by testSuite {
    tempDirFixture() asParameterForEach {
        // Each project switches between versions `1` and `2`, using `sc.current.parsed > "1"` as the direction.
        // Switching to `2` must apply the direct parameters and switching back to `1` the reverse ones,
        // which pins down both the direction handling and the order the pattern/replacement pairs are passed in.
        integrationTest("apply string replacement") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String value = MODERN_NAME;
                }
            """.trimIndent()

            runGradle(it, "stonecutterSwitchTo1")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String value = LEGACY_NAME;
                }
            """.trimIndent()
        }

        integrationTest("apply regex replacement") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    static final String VERSION = VERSION_2_0;
                    static final String API = API_MODERN_V2;
                }
            """.trimIndent()

            runGradle(it, "stonecutterSwitchTo1")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    static final String VERSION = VERSION_1_0;
                    static final String API = API_LEGACY_V1;
                }
            """.trimIndent()
        }

        integrationTest("apply regex flags") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String value = MODERN_MARKER;
                }
            """.trimIndent()

            runGradle(it, "stonecutterSwitchTo1")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String value = LEGACY_MARKER;
                }
            """.trimIndent()
        }

        integrationTest("apply legacy regex properties") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String value = MODERN_NAME;
                }
            """.trimIndent()

            runGradle(it, "stonecutterSwitchTo1")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String value = LEGACY_TEST_NAME;
                }
            """.trimIndent()
        }

        integrationTest("apply perl replacement") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String alpha = MOD_ALPHA_MODERN;
                    String beta = MOD_BETA_MODERN;
                }
            """.trimIndent()

            runGradle(it, "stonecutterSwitchTo1")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String alpha = MOD_ALPHA_LEGACY;
                    String beta = MOD_BETA_LEGACY;
                }
            """.trimIndent()
        }

        // Identifiers and direction are read from the same spec, so a swap there would silently
        // detach the rule from the marker it is supposed to be toggled by.
        integrationTest("toggle replacement ids") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String a = MODERN_ALPHA;
                    //~ !regex_id
                    String b = LEGACY_ALPHA;
                    String c = NEW_BETA;
                    //~ !perl_id
                    String d = OLD_BETA;
                }
            """.trimIndent()

            runGradle(it, "stonecutterSwitchTo1")
            it read "src/main/java/Example.java" shouldBe """
                public class Example {
                    String a = LEGACY_ALPHA;
                    //~ !regex_id
                    String b = LEGACY_ALPHA;
                    String c = OLD_BETA;
                    //~ !perl_id
                    String d = OLD_BETA;
                }
            """.trimIndent()
        }

        integrationTest("filter replacement files") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Scoped.java" shouldBe """
                public class Scoped {
                    String a = MODERN_SCOPED;
                    String b = LEGACY_OTHER;
                    String c = MODERN_SHARED;
                }
            """.trimIndent() + "\n"

            it read "src/main/java/Other.java" shouldBe """
                public class Other {
                    String a = LEGACY_SCOPED;
                    String b = MODERN_OTHER;
                    String c = MODERN_SHARED;
                }
            """.trimIndent() + "\n"
        }

        integrationTest("toggle filtered replacement id") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Other.java" shouldBe """
                public class Other {
                    //~ scoped_id
                    String a = MODERN_SCOPED;
                }
            """.trimIndent() + "\n"
        }

        integrationTest("enable filtered default replacement id") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/java/Scoped.java" shouldBe """
                public class Scoped {
                    String a = MODERN_SCOPED;
                }
            """.trimIndent() + "\n"

            it read "src/main/java/Other.java" shouldBe """
                public class Other {
                    String a = LEGACY_SCOPED;
                }
            """.trimIndent() + "\n"

            it read "src/main/java/Toggled.java" shouldBe """
                public class Toggled {
                    //~ scoped
                    String a = MODERN_SCOPED;
                }
            """.trimIndent() + "\n"
        }

        integrationTest("serialize replacement filters") {
            runGradle(it, "stonecutterSaveNodeModel")

            with(it read "versions/1/build/stonecutter-cache/node.json") {
                shouldContain("\"includes\"")
                shouldContain("\"excludes\"")
                shouldContain("**/Scoped.java")
                shouldContain("**/Other.java")
            }
        }

        integrationTest("apply replacements without comments") {
            runGradle(it, "stonecutterSwitchTo2")
            it read "src/main/resources/data.txt" shouldBe """
                name = MODERN_NAME
                note = //? if false is not a directive here
            """.trimIndent() + "\n"

            runGradle(it, "stonecutterSwitchTo1")
            it read "src/main/resources/data.txt" shouldBe """
                name = LEGACY_NAME
                note = //? if false is not a directive here
            """.trimIndent() + "\n"
        }
    }
}
