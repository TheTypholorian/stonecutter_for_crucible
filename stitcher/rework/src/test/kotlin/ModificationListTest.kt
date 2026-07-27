@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.TestCompartment
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.document.edit.ModificationEvent
import dev.kikugie.stitcher.document.edit.ModificationList
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

val ModificationListTest by testSuite(compartment = { TestCompartment.Sequential }) {
    testFixture { ModificationList() } asParameterForAll {
        test("add to empty") {
            it.add(4..<9, "Hello") shouldBe true
            it.entries shouldContainExactly listOf(event(4..<9, "Hello"))
        }

        test("insert first") {
            it.add(1..<3, "<<") shouldBe true
            it.entries shouldContainExactly listOf(event(1..<3, "<<"), event(4..<9, "Hello"))
        }

        test("append last") {
            it.add(20..<23, ">>") shouldBe true
            it.entries shouldContainExactly listOf(event(1..<3, "<<"), event(4..<9, "Hello"), event(20..<23, ">>"))
        }

        test("insert middle") {
            it.add(10..<15, "World")
            it.entries shouldContainExactly listOf(event(1..<3, "<<"), event(4..<9, "Hello"), event(10..<15, "World"), event(20..<23, ">>"))
        }

        test("insert merging") {
            it.add(9..<10, " ")
            it.entries shouldContainExactly listOf(event(1..<3, "<<"), event(4..<15, "Hello World"), event(20..<23, ">>"))
        }
    }
}

private fun ModificationList.add(range: IntRange, text: String): Boolean =
    add(event(range, text))

private fun event(range: IntRange, text: String): ModificationEvent =
    ModificationEvent(range.first, range.last + 1, text)
