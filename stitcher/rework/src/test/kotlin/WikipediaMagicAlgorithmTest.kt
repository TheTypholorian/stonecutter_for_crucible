@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.util.distanceTo
import dev.kikugie.stitcher.util.findClosest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val DamerauLevenshteinDistanceTest by testSuite {
    val cases = buildList {
        this += Triple("", "test", 4)
        this += Triple("", "", 0)
        this += Triple("test", "test", 0)
        this += Triple("test", "Test", 1)
        this += Triple("test", "test1", 1)
        this += Triple("test", "tset", 1)
        this += Triple("test", "apple", 4)
        this += Triple("test", "run", 3)
    }

    for ((first, second, third) in cases) test("'$first' to '$second'") {
        first distanceTo second shouldBe third
    }

    test("find closes unbound") {
        "file".findClosest(listOf("orange", "juice")) shouldBe "juice"
    }

    test("find closes bound") {
        "file".findClosest(listOf("orange", "juice"), 1).shouldBeNull()
    }
}
