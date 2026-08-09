@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.document.DocumentLocation
import dev.kikugie.stitcher.document.SourceDocument
import io.kotest.matchers.shouldBe
import matcher.IMMEDIATE_THROW
import java.nio.file.Path

private const val TEXT = "abc\ndef\nghi"
private val FILE: Path = Path.of("source.txt")

val SourceDocumentTest by testSuite {
    testFixture { SourceDocument(FILE, TEXT, IMMEDIATE_THROW) } asParameterForEach {
        testSuite("CharSequence delegation") {
            test("length matches the backing text") {
                it.length shouldBe 11
            }

            test("character access matches the backing text") {
                it[0] shouldBe 'a'
                it[4] shouldBe 'd'
                it[10] shouldBe 'i'
            }

            test("subSequence matches the backing text") {
                it.subSequence(4, 7).toString() shouldBe "def"
            }

            test("subSequence of the whole document") {
                it.subSequence(0, it.length).toString() shouldBe TEXT
            }
        }

        testSuite("file") {
            test("exposes the backing path") {
                it.file shouldBe FILE
            }
        }

        testSuite("locate") {
            test("start of a line") {
                it.locate(4) shouldBe DocumentLocation(1, 0)
            }

            test("newline counts towards the preceding line") {
                it.locate(3) shouldBe DocumentLocation(0, 3)
            }

            test("last character") {
                it.locate(10) shouldBe DocumentLocation(2, 2)
            }

            test("out of bounds is unknown") {
                it.locate(-1) shouldBe DocumentLocation.UNKNOWN
                it.locate(11) shouldBe DocumentLocation.UNKNOWN
            }
        }

        testSuite("indexOf") {
            test("start of a line") {
                it.indexOf(1, 0) shouldBe 4
            }

            test("column within a line") {
                it.indexOf(2, 2) shouldBe 10
            }

            test("negative arguments return -1") {
                it.indexOf(-1, 0) shouldBe -1
                it.indexOf(0, -1) shouldBe -1
            }

            test("line beyond the last returns length") {
                it.indexOf(10, 0) shouldBe 11
            }
        }

        testSuite("appendTo") {
            test("appends the requested range") {
                append(it, 4, 7) shouldBe "def"
            }

            test("appends the whole document") {
                append(it, 0, it.length) shouldBe TEXT
            }

            test("empty range appends nothing") {
                append(it, 5, 5) shouldBe ""
            }
        }
    }
}

private fun append(doc: SourceDocument, start: Int, end: Int): String =
    buildString { doc.appendTo(this, start, end) }
