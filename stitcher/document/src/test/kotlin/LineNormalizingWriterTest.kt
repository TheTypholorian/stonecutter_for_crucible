@file:Suppress("PublicApiImplicitType")

import de.infix.testBalloon.framework.core.testSuite
import dev.kikugie.stitcher.document.io.LineNormalizingWriter
import io.kotest.matchers.shouldBe
import java.io.StringWriter

val LineNormalizingWriterTest by testSuite {
    test("write empty") {
        "" write "\n" shouldBe ""
    }

    test("write no line breaks") {
        "Hello World!" write "\n" shouldBe "Hello World!"
    }

    test("write with LF to LF") {
        "Hello\nWorld!" write "\n" shouldBe "Hello\nWorld!"
    }

    test("write with LF to CR") {
        "Hello\nWorld!" write "\r" shouldBe "Hello\rWorld!"
    }

    test("write with CR to LF") {
        "Hello\rWorld!" write "\n" shouldBe "Hello\nWorld!"
    }

    test("write with LF to CRLF") {
        "Hello\nWorld!" write "\r\n" shouldBe "Hello\r\nWorld!"
    }

    test("write with CRLF to CRLF") {
        "Hello\r\nWorld!" write "\r\n" shouldBe "Hello\r\nWorld!"
    }

    test("write with CRLF to LF") {
        "Hello\r\nWorld!" write "\n" shouldBe "Hello\nWorld!"
    }
}

private infix fun String.write(separator: String): String {
    val writer = StringWriter()
    LineNormalizingWriter(writer, separator).write(this)
    return writer.toString()
}
