package integration

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.string.shouldContain
import util.GradleSetup
import util.config

class GroovyCompatibilityTest : ShouldSpec({
    context("settings") {
        should("accept combined setup").config<GradleSetup> { build("stonecutterGenerate") }
        should("use implicit shared").config<GradleSetup> { build("stonecutterGenerate") }
        should("use explicit shared").config<GradleSetup> { build("stonecutterGenerate") }
        should("declare branch versions").config<GradleSetup> { build(":subproject:3:stonecutterGenerate") }
    }

    context("controller") {
        should("use parameters function").config<GradleSetup> {
            build {
                with(output) {
                    shouldContain("Parameters for :1")
                    shouldContain("Parameters for :2")
                }
            }
        }
    }

    context("build") {
        should("access parsed wrapper").config<GradleSetup> {
            build()
        }
    }
})