package integration

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.paths.shouldContainFile
import io.kotest.matchers.paths.shouldNotContainFile
import io.kotest.matchers.should
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import util.GradleSetup
import util.config
import util.read
import kotlin.io.path.exists

class SettingsExtensionTest : ShouldSpec({
    should("create extension alias").config<GradleSetup> {
        build()
    }

    should("fail with disabled alias").config<GradleSetup> {
        fail()
    }

    context("script resolution") {
        should("create subproject directories").config<GradleSetup> {
            build { dir.resolve("versions") should { it.exists() } }
        }

        should("create subproject root").config<GradleSetup> {
            build { dir.resolve("example") shouldContainFile "stonecutter.gradle.kts" }
        }

        should("generate kts scripts by default").config<GradleSetup> {
            build(":1:hello") {
                output shouldContain "1!"
                dir shouldContainFile "stonecutter.gradle.kts"
            }
        }

        should("explicitly use groovy scripts").config<GradleSetup> {
            build(":1:hello") {
                output shouldContain "1!"
                dir shouldContainFile "stonecutter.gradle"
            }
        }

        should("recognize existing groovy scripts").config<GradleSetup> {
            build { dir shouldNotContainFile "stonecutter.gradle.kts" }
        }

        should("print groovy compatibility warning").config<GradleSetup> {
            build { output shouldContain "Warning:" }
        }

        should("mute compat warning with property").config<GradleSetup> {
            build { output shouldNotContain "Warning:" }
        }
    }

    context("tree builder") {
        should("use existing shared action").config<GradleSetup> {
            build { dir.resolve("versions") should { it.exists() } }
        }

        should("apply shared explicitly").config<GradleSetup> {
            build { dir.resolve("versions") should { it.exists() } }
        }

        should("not allow inherit call in root").config<GradleSetup> {
            fail()
        }

        should("not allow inherit call in default branch").config<GradleSetup> {
            fail()
        }

        should("implicitly assign vcs version").config<GradleSetup> {
            build { dir read "stonecutter.gradle.kts" shouldContain ("\"3\"") }
        }

        should("overwrite vcs version").config<GradleSetup> {
            build { dir read "stonecutter.gradle.kts" shouldContain ("\"1\"") }
        }

        should("validate branch names").config<GradleSetup> {
            fail()
        }

        should("validate version consistency").config<GradleSetup> {
            fail()
        }

        should("have no vers function").config<GradleSetup> {
            fail()
        }

        should("include deep projects").config<GradleSetup> {
            build()
        }
    }

    context("tree deserialization") {
        should("accept json files").config<GradleSetup> { build() }
        should("accept json5 files").config<GradleSetup> { build() }
        should("accept toml files").config<GradleSetup> { build() }
        should("accept yaml files").config<GradleSetup> { build() }
        should("reject unknown files").config<GradleSetup> { fail() }
        should("define project branches").config<GradleSetup> { build() }
        should("define inverted branches").config<GradleSetup> { build() }
        should("define extended nodes").config<GradleSetup> { build() }
        should("load_files_in_action").config<GradleSetup> { build() }
    }
})