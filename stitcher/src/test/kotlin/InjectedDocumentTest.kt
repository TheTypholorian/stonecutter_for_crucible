@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.DocumentLocation
import dev.kikugie.stitcher.document.InjectedDocument
import dev.kikugie.stitcher.document.SourceDocument
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.issue.output.FullProblemFormatter
import io.kotest.matchers.shouldBe
import java.nio.file.Path

val InjectedDocumentTest by testSuite {
    testSuite("content") {
        test("whole document without edits is unchanged") {
            inject("0123456789", 0, 10).fullText() shouldBe "0123456789"
        }

        test("window without edits copies the source slice") {
            inject("0123456789", 2, 8).fullText() shouldBe "234567"
        }

        test("insertion in the middle keeps the surrounding source") {
            inject("abcdef", 0, 6) { insert(3, "X") }.fullText() shouldBe "abcXdef"
        }

        test("insertion at the start") {
            inject("abcdef", 0, 6) { insert(0, "X") }.fullText() shouldBe "Xabcdef"
        }

        test("insertion at the end") {
            inject("abcdef", 0, 6) { insert(6, "Y") }.fullText() shouldBe "abcdefY"
        }

        test("replacement swaps a shorter run") {
            inject("0123456789", 0, 10) { replace(2, 5, "XY") }.fullText() shouldBe "01XY56789"
        }

        test("deletion removes a run") {
            inject("0123456789", 0, 10) { delete(2, 5) }.fullText() shouldBe "0156789"
        }

        test("multiple edits keep the gaps between them") {
            inject("abcdef", 0, 6) { insert(1, "X"); insert(4, "Y") }.fullText() shouldBe "aXbcdYef"
        }

        test("edit index is relative to the document, within the window") {
            // Window [2, 8) is "234567"; inserting at document index 3 lands between '2' and '3'.
            inject("0123456789", 2, 8) { insert(3, "X") }.fullText() shouldBe "2X34567"
        }
    }

    testSuite("length and get") {
        testFixture { inject("0123456789", 0, 10) { replace(2, 5, "XY") } } asParameterForEach {
            test("length reflects the modified text") {
                it.length shouldBe 9
            }

            test("get returns modified characters in range") {
                it[0] shouldBe '0'
                it[2] shouldBe 'X'
                it[3] shouldBe 'Y'
                it[4] shouldBe '5'
            }
        }

        // Window "234567" over "0123456789"; local index 0 is source index 2.
        testFixture { inject("0123456789", 2, 8) } asParameterForEach {
            test("get before the window reads the parent") {
                it[-1] shouldBe '1'
                it[-2] shouldBe '0'
            }

            test("get past the window reads the parent") {
                it[it.length] shouldBe '8'
                it[it.length + 1] shouldBe '9'
            }
        }
    }

    testSuite("locate") {
        // Parent "ab\ncd": line starts 0 and 3; newline at index 2.
        testFixture { inject("ab\ncd", 0, 5) } asParameterForEach {
            test("start of the second line") {
                it.locate(3) shouldBe DocumentLocation(1, 0)
            }

            test("newline counts towards the preceding line") {
                it.locate(2) shouldBe DocumentLocation(0, 2)
            }

            test("out of bounds is unknown") {
                it.locate(-1) shouldBe DocumentLocation.UNKNOWN
                it.locate(5) shouldBe DocumentLocation.UNKNOWN
            }
        }

        // Window "b\nc" over "ab\ncd"; local index 0 is source index 1.
        testFixture { inject("ab\ncd", 1, 4) } asParameterForEach {
            test("index before the window locates in the parent") {
                it.locate(-1) shouldBe DocumentLocation(0, 0)
            }

            test("index past the window locates in the parent") {
                it.locate(it.length) shouldBe DocumentLocation(1, 1)
            }
        }

        // Modified "01XY56789": XY replaces source 2..5.
        testFixture { inject("0123456789", 0, 10) { replace(2, 5, "XY") } } asParameterForEach {
            test("replacement text maps onto its source range") {
                it.locate(2) shouldBe DocumentLocation(0, 2)
                it.locate(3) shouldBe DocumentLocation(0, 3)
            }

            test("source after the replacement is offset by the delta") {
                it.locate(4) shouldBe DocumentLocation(0, 5)
                it.locate(8) shouldBe DocumentLocation(0, 9)
            }
        }
    }

    testSuite("indexOf") {
        // Parent "abc\ndef\nghi": line starts 0, 4, 8.
        testFixture { inject("abc\ndef\nghi", 0, 11) } asParameterForEach {
            test("resolves a source position without edits") {
                it.indexOf(1, 0) shouldBe 4
                it.indexOf(2, 2) shouldBe 10
            }

            test("negative arguments return -1") {
                it.indexOf(-1, 0) shouldBe -1
                it.indexOf(0, -1) shouldBe -1
            }
        }

        test("line beyond the last clamps to length") {
            inject("abc\ndef\nghi", 0, 11).indexOf(10, 0) shouldBe 11
        }

        // Window "def\nghi" starting at source index 4.
        testFixture { inject("abc\ndef\nghi", 4, 11) } asParameterForEach {
            test("result is relative to the window start") {
                it.indexOf(1, 0) shouldBe 0
                it.indexOf(2, 0) shouldBe 4
            }
        }

        // Modified "01XY56789": XY replaces source 2..5, so positions after it shift by -1.
        testFixture { inject("0123456789", 0, 10) { replace(2, 5, "XY") } } asParameterForEach {
            test("source before the edit is unchanged") {
                it.indexOf(0, 1) shouldBe 1
            }

            test("source within the edit collapses to the replacement start") {
                it.indexOf(0, 2) shouldBe 2
                it.indexOf(0, 4) shouldBe 2
            }

            test("source after the edit is offset by the delta") {
                it.indexOf(0, 5) shouldBe 4
                it.indexOf(0, 9) shouldBe 8
            }
        }
    }

    testSuite("appendTo and subSequence") {
        // Window "234567" over "0123456789".
        testFixture { inject("0123456789", 2, 8) } asParameterForEach {
            test("appends a range within the window") {
                it.append(1, 4) shouldBe "345"
            }

            test("a negative start reaches into the parent before the window") {
                it.append(-2, 3) shouldBe "01234"
            }

            test("an end past the length reaches into the parent after the window") {
                it.append(4, 8) shouldBe "6789"
            }

            test("subSequence matches appendTo over the whole document") {
                it.subSequence(0, it.length).toString() shouldBe it.fullText()
            }
        }
    }
}

private fun source(text: String) = SourceDocument(Path.of("injected.txt"), text) {
    throw RuntimeException(it.accept(FullProblemFormatter))
}

private fun inject(text: String, start: Int, end: Int, edits: ModificationTracker.() -> Unit = {}): InjectedDocument {
    val parent = source(text)
    val tracker = ModificationTracker(parent, start, end).apply(edits)
    return InjectedDocument(parent, start, end, tracker)
}

private fun Document.fullText(): String {
    val doc = this
    return buildString { doc.appendTo(this, 0, doc.length) }
}

private fun Document.append(start: Int, end: Int): String {
    val doc = this
    return buildString { doc.appendTo(this, start, end) }
}
