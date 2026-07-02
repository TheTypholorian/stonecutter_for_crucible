package v2

import dev.kikugie.stitcher.document.DocumentLineIndex
import dev.kikugie.stitcher.document.DocumentLocation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class DocumentLineIndexTest : FunSpec({
    context("build") {
        test("empty document") {
            DocumentLineIndex("").positions.shouldBeEmpty()
        }

        test("single line") {
            DocumentLineIndex("Hello World!").positions.shouldContainExactly(0)
        }

        test("end line break") {
            DocumentLineIndex("Hello World!\n").positions.shouldContainExactly(0, 13)
        }
    }

    context("locate") {
        test("empty document") {
            "".locate(4) shouldBe DocumentLocation.UNKNOWN
        }

        test("out of bounds") {
            "Hello World!".locate(99) shouldBe DocumentLocation.UNKNOWN
        }

        test("line start") {
            "Hello\nWorld!".locate(6) shouldBe DocumentLocation(1, 0)
        }

        test("line end") {
            "Hello\nWorld!".locate(11) shouldBe DocumentLocation(1, 5)
        }

        test("line middle") {
            "Hello\nWorld!\n".locate(8) shouldBe DocumentLocation(1, 2)
        }
    }

    context("reverse") {
        test("empty document") {
            "".reverse(99, 99) shouldBe -1
        }

        test("line start") {
            "Hello\nWorld!".reverse(1, 0) shouldBe 6
        }

        test("line end") {
            "Hello\nWorld!".reverse(0, 5) shouldBe 5
        }

        test("text oob") {
            "Hello\nWorld!".reverse(99, 0) shouldBe -1
        }
    }
})

private fun String.locate(pos: Int): DocumentLocation =
    DocumentLineIndex(this).locate(pos)

private fun String.reverse(l: Int, c: Int): Int =
    DocumentLineIndex(this).reverse(l, c)
