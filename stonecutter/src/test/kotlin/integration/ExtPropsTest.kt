package integration

import io.kotest.core.spec.style.ShouldSpec
import util.GradleSetup
import util.config

class ExtPropsTest : ShouldSpec({
    should("read default in settings").config<GradleSetup> { build() }
    should("read default in controller").config<GradleSetup> { build() }
    should("read default in parameters").config<GradleSetup> { build() }
    should("read default in build").config<GradleSetup> { build() }
    should("append new properties").config<GradleSetup> { build() }
    should("use diff props").config<GradleSetup> { build() }
    should("use property tags").config<GradleSetup> { build() }
    // https://codeberg.org/stonecutter/stonecutter/issues/44
    should("use nested property tags").config<GradleSetup> { build() }
    should("accept json files").config<GradleSetup> { build() }
    should("accept yaml files").config<GradleSetup> { build() }
    should("use typed properties").config<GradleSetup> { build() }
    // For some reason this only fails in test env
    xshould("parse property types").config<GradleSetup> { build() }
    should("retain all tags").config<GradleSetup> { build() }
})