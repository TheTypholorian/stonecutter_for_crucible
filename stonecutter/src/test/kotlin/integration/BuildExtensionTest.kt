package integration

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContainOnlyOnce
import util.GradleSetup
import util.config
import util.read

class BuildExtensionTest : ShouldSpec({
    should("create extension alias").config<GradleSetup> { build() }
    should("fail with disabled alias").config<GradleSetup> { fail() }

    context("process parameters") {
        should("allow regular values").config<GradleSetup> { build() }
        should("validate constant names").config<GradleSetup> { fail() }
        should("validate swap names").config<GradleSetup> { fail() }
        should("validate dependency names").config<GradleSetup> { fail() }

        should("allow shared replacement ids").config<GradleSetup> { build() }
        should("validate replacement ids").config<GradleSetup> { fail() }
        should("require replacement direction").config<GradleSetup> { fail() }
        should("register multiple replacements").config<GradleSetup> {
            build("stonecutterSwitchTo2") {
                with(dir read "src/main/java/Example.java") {
                    shouldContainOnlyOnce("a.d.c")
                    shouldContainOnlyOnce("1.3.4")
                }
            }
        }
    }

    context("file processing") {
        should("use project version").config<GradleSetup> {
            build("stonecutterSwitchTo1.0-example") {
                dir read "src/main/java/Example.java" shouldBe """
                    //? if <1.0
                    //class Example {}
                """.trimIndent()
            }
        }
    }
})