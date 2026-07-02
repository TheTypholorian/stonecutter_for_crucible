package v2

import dev.kikugie.stitcher.document.io.LineNormalizingReader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LineNormalizingReaderTest : FunSpec({
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
})

private infix fun String.shouldBeReadAs(other: String): String =
    LineNormalizingReader(reader()).readText() shouldBe other
