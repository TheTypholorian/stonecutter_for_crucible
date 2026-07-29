@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.TestCompartment
import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.document.edit.ModificationEvent
import dev.kikugie.stitcher.document.edit.ModificationList
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

val ModificationListTest by testSuite(compartment = { TestCompartment.Sequential }) {
    testFixture { ModificationList() } asContextForEach {
        test("add to empty") {
            add(4..<9, "Hello") shouldBe true
            entries shouldContainExactly listOf(event(4..<9, "Hello"))
        }

        test("insert first") {
            add(1..<3, "<<") shouldBe true
            entries shouldContainExactly listOf(event(1..<3, "<<"), event(4..<9, "Hello"))
        }

        test("append last") {
            add(20..<23, ">>") shouldBe true
            entries shouldContainExactly listOf(event(1..<3, "<<"), event(4..<9, "Hello"), event(20..<23, ">>"))
        }

        test("insert middle") {
            add(10..<15, "World")
            entries shouldContainExactly listOf(event(1..<3, "<<"), event(4..<9, "Hello"), event(10..<15, "World"), event(20..<23, ">>"))
        }

        test("insert merging") {
            add(9..<10, " ")
            entries shouldContainExactly listOf(event(1..<3, "<<"), event(4..<15, "Hello World"), event(20..<23, ">>"))
        }

        test("a deletion after a replacement keeps the replacement text") {
            add(4..<5, "\n")
            add(5..<7, "")
            entries shouldContainExactly listOf(event(4..<7, "\n"))
        }

        test("adjacent deletions collapse into a single empty replacement") {
            add(4..<6, "")
            add(6..<9, "")
            entries shouldContainExactly listOf(event(4..<9, ""))
        }

        test("an insertion after a replacement is appended to it") {
            add(4..<9, "Hello")
            add(9..<9, "!!")
            entries shouldContainExactly listOf(event(4..<9, "Hello!!"))
        }

        test("a replacement added before an existing deletion keeps its text") {
            add(5..<7, "")
            add(3..<5, "X")
            entries shouldContainExactly listOf(event(3..<7, "X"))
        }

        test("a chain of touching edits folds into one event") {
            add(4..<6, "")
            add(6..<7, "X")
            add(7..<9, "")
            entries shouldContainExactly listOf(event(4..<9, "X"))
        }
    }
}

private fun ModificationList.add(range: IntRange, text: String): Boolean =
    add(event(range, text))

private fun event(range: IntRange, text: String): ModificationEvent =
    ModificationEvent(range.first, range.last + 1, text)
