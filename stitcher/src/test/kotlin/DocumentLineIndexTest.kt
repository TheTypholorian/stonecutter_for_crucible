@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.document.DocumentLineIndex
import dev.kikugie.stitcher.document.DocumentLocation
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

private const val TEXT = """Lorem ipsum dolor sit amet consectetur adipiscing elit.
Sit amet consectetur adipiscing elit quisque faucibus ex.
Adipiscing elit quisque faucibus ex sapien vitae pellentesque.
"""

val DocumentLineIndexTest by testSuite {
    testSuite("positions") {
        test("empty list for empty text") {
            with(DocumentLineIndex("")) {
                positions shouldHaveSize 0
                length shouldBe 0
            }
        }

        test("first line starts at zero") {
            with(DocumentLineIndex("Hello!")) {
                positions shouldContainExactly listOf(0)
                length shouldBe 6
            }
        }

        test("trailing line break counts") {
            with(DocumentLineIndex("Hello!\n")) {
                positions shouldContainExactly listOf(0, 7)
                length shouldBe 7
            }
        }

        test("consecutive line breaks have empty lines") {
            with(DocumentLineIndex("Hello!\n\n")) {
                positions shouldContainExactly listOf(0, 7, 8)
                length shouldBe 8
            }
        }

        // Line endings are normalised to \n
        test("carriage return is not recognised as a line break") {
            with(DocumentLineIndex("Hello!\r\n")) {
                positions shouldContainExactly listOf(0, 8)
                length shouldBe 8
            }
        }
    }

    testSuite("locate") {
        test("empty document always emits unknown") {
            DocumentLineIndex("").locate(0) shouldBe DocumentLocation.UNKNOWN
        }

        testFixture { DocumentLineIndex(TEXT) } asParameterForEach {
            test("negative index is unknown") {
                it.locate(-1) shouldBe DocumentLocation.UNKNOWN
            }

            test("index at length is unknown") {
                it.locate(177) shouldBe DocumentLocation.UNKNOWN
            }

            test("index beyond length is unknown") {
                it.locate(1000) shouldBe DocumentLocation.UNKNOWN
            }

            test("start of the first line") {
                it.locate(0) shouldBe DocumentLocation(0, 0)
            }

            test("middle of a line") {
                it.locate(10) shouldBe DocumentLocation(0, 10)
            }

            test("newline counts towards the preceding line") {
                it.locate(55) shouldBe DocumentLocation(0, 55)
            }

            test("start of the second line") {
                it.locate(56) shouldBe DocumentLocation(1, 0)
            }

            test("start of the last line") {
                it.locate(114) shouldBe DocumentLocation(2, 0)
            }

            test("last character of the document") {
                it.locate(176) shouldBe DocumentLocation(2, 62)
            }
        }
    }

    testSuite("index") {
        test("empty document returns -1") {
            DocumentLineIndex("").index(0, 0) shouldBe -1
        }

        testFixture { DocumentLineIndex(TEXT) } asParameterForEach {
            test("negative line returns -1") {
                it.index(-1, 0) shouldBe -1
            }

            test("negative column returns -1") {
                it.index(0, -1) shouldBe -1
            }

            test("start of the first line") {
                it.index(0, 0) shouldBe 0
            }

            test("column within a line") {
                it.index(0, 10) shouldBe 10
            }

            test("start of the second line") {
                it.index(1, 0) shouldBe 56
            }

            test("start of the last line") {
                it.index(2, 0) shouldBe 114
            }

            test("column beyond a line clamps to its newline") {
                it.index(0, 1000) shouldBe 55
            }

            test("line beyond the last returns length") {
                it.index(10, 0) shouldBe 177
            }

            test("locate and index round-trip") {
                for (i in 0 until it.length) {
                    val (line, column) = it.locate(i)
                    it.index(line, column) shouldBe i
                }
            }
        }
    }
}
