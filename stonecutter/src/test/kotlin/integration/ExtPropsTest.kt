package integration

import gradle.GradleTest
import gradle.should
import io.kotest.core.spec.style.ShouldSpec

class ExtPropsTest : GradleTest, ShouldSpec({
    should("read default in settings") { _, build ->
        build.run()
    }

    should("read default in controller") { _, build ->
        build.run()
    }

    should("read default in build") { _, build ->
        build.run()
    }

    should("append new properties") { _, build ->
        build.run()
    }

    should("use diff props") { _ , build ->
        build.run()
    }

    should("use property tags") { _ , build ->
        build.run()
    }
})