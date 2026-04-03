package integration

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.test.config.TestConfig
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import util.GradleSetup
import util.config
import util.read
import kotlin.io.path.useLines

@Deprecated("Should be migrated to dedicated tests")
class OldRandomTest : ShouldSpec({
    should("simple version").config<GradleSetup> {
        build(":1:run") { output shouldContain "Hello world!" }
        build(":2:run") { output shouldNotContain "Hello world!" }
    }

    should("unclosed scope").config<GradleSetup> {
        fail("run") { output shouldContain "Example.java:3:19" }
    }

    should("simple swap").config<GradleSetup> {
        build(":1:run") { output shouldContain "Hello Tim!" }
        build(":2:run") { output shouldContain "Hello Lace!" }
    }

    should("swap arguments").config<GradleSetup> {
        build(":1:run") { output shouldContain "Hello Lace!" }
        build(":2:run") { output shouldContain "Bye Lace!" }
    }

    should("swap indents").config<GradleSetup> {
        build("stonecutterSwitchTo2") {
            dir.resolve("src/main/java/Example.java")
                .useLines { lines -> lines.first { "Hello Lace!" in it } }
                .shouldStartWith(" ".repeat(8))
        }
    }

    should("simple constant").config<GradleSetup> {
        build(":1:run") { output shouldContain "Hello world!" }
        build(":2:run") { output shouldNotContain "Hello world!" }
    }

    should("simple dependency").config<GradleSetup> {
        build(":1:run") { output shouldContain "Hello world!" }
        build(":2:run") { output shouldNotContain "Hello world!" }
    }

    should("if-else chain").config<GradleSetup> {
        for (i in 1..4) build(":$i:run") { output shouldContain "$i!" }
    }

    // TODO: Not critical
    should("duplicate else").config<GradleSetup>(TestConfig(enabled = false)) {
        fail("run") { output shouldContain "Example.java:7:16" }
    }

    should("included comments").config<GradleSetup> {
        build("stonecutterSwitchTo2") {
            dir read "src/main/java/Example.java" shouldContain "/^¹nested¹^/"
        }

        build("stonecutterSwitchTo1") {
            dir read "src/main/java/Example.java" shouldContain "/^nested^/"
        }
    }

    should("nested conditions").config<GradleSetup> {
        build("stonecutterSwitchTo1.20.1")
        build(":1.20.1:run") { output shouldContain "CASE B" }
    }

    /**
     * Default `$ swap >>` scopes should first skip the empty region,
     * and then capture everything until the first whitespace.
     * @see <a href="https://codeberg.org/stonecutter/stonecutter/issues/17">#17</a>
     */
    should("word scope default").config<GradleSetup> {
        build(":2:run") { output shouldContain "Bye Lace!" }
    }

    /**
     * Custom matchers `$ swap >> 'str'` should match the string,
     * without including it in the scope.
     * @see <a href="https://codeberg.org/stonecutter/stonecutter/issues/17">#17</a>
     */
    should("word scope custom").config<GradleSetup> {
        build(":2:run") { output shouldContain "Bye Tim!" }
    }

    /**
     * Custom matchers `$ swap >>+ 'str'` should match the string,
     * **including** it in the scope.
     * @see <a href="https://codeberg.org/stonecutter/stonecutter/issues/17">#17</a>
     */
    should("word scope capturing").config<GradleSetup> {
        build(":2:run") { output shouldContain "Bye Tim!" }
    }

    /**
     * Custom matchers that couldn't be satisfied until
     * the scope was closed should report an error.
     * @see <a href="https://codeberg.org/stonecutter/stonecutter/issues/17">#17</a>
     */
    should("word scope unmatched").config<GradleSetup> {
        fail(":2:run") { this.output shouldContain "Lookup string not found" }
    }
})