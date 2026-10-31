@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.document.io.LineNormalizingReader
import io.kotest.matchers.shouldBe

val LineNormalizingReaderTest by testSuite {
    test("read empty") {
        "" shouldBeReadAs ""
    }

    test("read no line breaks") {
        "Hello World!" shouldBeReadAs "Hello World!"
    }

    test("read with LF") {
        "Hello\nWorld!" shouldBeReadAs "Hello\nWorld!"
    }

    test("read with CR") {
        "Hello\rWorld!" shouldBeReadAs "Hello\nWorld!"
    }

    test("read with CRLF") {
        "Hello\r\nWorld!" shouldBeReadAs "Hello\nWorld!"
    }

    test("read with mixed CR/LF") {
        "Hello\r\r\n\nWorld!" shouldBeReadAs "Hello\n\n\nWorld!"
    }

    test("read ending with LF") {
        val reader = LineNormalizingReader("Hello!\n".reader())
        reader.read(CharArray(16)) shouldBe 7
    }

    test("read ending with CR") {
        val reader = LineNormalizingReader("Hello!\r".reader())
        reader.read(CharArray(16)) shouldBe 7
    }

    test("read ending with CRLF") {
        val reader = LineNormalizingReader("Hello!\r\n".reader())
        reader.read(CharArray(16)) shouldBe 7
    }

    test("read ending with CRLF split") {
        val reader = LineNormalizingReader("Hello!\r\n".reader())
        reader.read(CharArray(16), off = 0, len = 7) shouldBe 7
        reader.read(CharArray(16)) shouldBe -1
    }

    test("read more that requested") {
        val reader = LineNormalizingReader("Hello!".reader())
        reader.read(CharArray(16), off = 0, len = 16) shouldBe 6
    }

    test("detect no separator") {
        "Hello World!" shouldDetect null
    }

    test("detect LF") {
        "Hello\nWorld!" shouldDetect "\n"
    }

    test("detect CR") {
        "Hello\rWorld!" shouldDetect "\r"
    }

    test("detect CRLF") {
        "Hello\r\nWorld!" shouldDetect "\r\n"
    }

    test("detect CR at the end") {
        "Hello!\r" shouldDetect "\r"
    }

    test("detect first of mixed separators") {
        "Hello\n\r\nWorld!" shouldDetect "\n"
        "Hello\r\n\nWorld!" shouldDetect "\r\n"
        "Hello\r\r\nWorld!" shouldDetect "\r"
    }

    test("detect CRLF split between reads") {
        val reader = LineNormalizingReader("Hello!\r\n".reader())
        reader.read(CharArray(16), off = 0, len = 7)
        reader.separator shouldBe null
        reader.read(CharArray(16))
        reader.separator shouldBe "\r\n"
    }
}

private infix fun String.shouldBeReadAs(other: String): String =
    LineNormalizingReader(reader()).readText() shouldBe other

private infix fun String.shouldDetect(separator: String?): String? =
    LineNormalizingReader(reader()).apply { readText() }.separator shouldBe separator
