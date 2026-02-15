package integration

import gradle.GradleTest
import gradle.fail
import gradle.should
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class EasterEggTest : GradleTest, ShouldSpec({
    // Test for "Roses are red; Violets are blue; <error> on line <10n + 2>"
    should("appreciate poetry") { _, build ->
        with(build.fail("stonecutterSwitchTo2").buildResult.output) {
            shouldNotContain("on line 2\n")
            shouldNotContain("on line 12\n")
            shouldContain("on line 22\n")
            shouldContain("Roses are red")
            shouldContain("Violets are blue")
        }
    }
})