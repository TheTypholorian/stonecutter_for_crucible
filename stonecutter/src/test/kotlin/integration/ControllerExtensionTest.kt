package integration

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainOnlyOnce
import util.GradleSetup
import util.config
import util.read

class ControllerExtensionTest : ShouldSpec({
    should("create extension alias").config<GradleSetup> { build() }
    should("fail with disabled alias").config<GradleSetup> { fail() }
    should("switch active with alias").config<GradleSetup> {
        build("stonecutterSwitchTo2") {
            dir read "stonecutter.gradle.kts" shouldContain "sc active \"2\""
        }
    }

    context("plugin initialization") {
        should("fail without active call").config<GradleSetup> { fail() }
        should("warn with base plugin").config<GradleSetup> {
            build { output shouldContain "Stonecutter branch root" }
        }
        should("validate active parameter").config<GradleSetup> { fail() }
        should("accept file active").config<GradleSetup> { build() }
    }

    context("stonecutter flags") {
        should("not apply to build").config<GradleSetup> { fail() }
        should("apply before init").config<GradleSetup> { build() }
        should("override default receiver").config<GradleSetup> { build("stonecutterGenerate") }
    }

    context("task operations") {
        should("depend on uninitialized tasks").config<GradleSetup> {
            build("sayHelloAndGoodbye") {
                with(output) {
                    shouldContain("Hello!")
                    shouldContainOnlyOnce("Goodbye!")
                    lastIndexOf("Hello") shouldBeLessThan indexOf("Goodbye!")
                }
            }
        }
    }

    context("file handlers") {
        should("inherit custom handler").config<GradleSetup> {
            build("stonecutterSwitchTo2") {
                dir read "src/main/example.custom" shouldContain "#comment"
            }
        }

        should("override existing handler").config<GradleSetup> {
            build("stonecutterSwitchTo2") {
                dir read "src/main/java/Example.java" shouldContain "//int one = 1;"
            }
        }

        should("throw on invalid handler").config<GradleSetup> { fail() }
    }
})