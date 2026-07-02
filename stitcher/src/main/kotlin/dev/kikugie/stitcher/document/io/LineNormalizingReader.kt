package dev.kikugie.stitcher.document.io

import java.io.Reader

class LineNormalizingReader(private val source: Reader, lock: Any = source) : Reader(lock) {
    private var lastWasCR: Boolean = false

    override fun close(): Unit = source.close()
    override fun read(cbuf: CharArray?, off: Int, len: Int): Int {
        if (cbuf == null || len == 0) return 0

        for (i in 0 until len) when (val code = next()) {
            -1 -> return if (i == 0) -1 else i
            else -> cbuf[off + i] = code.toChar()
        }
        return len
    }

    private tailrec fun next(): Int = when (val code = source.read()) {
        -1 -> -1

        '\r'.code -> {
            lastWasCR = true
            '\n'.code
        }

        '\n'.code -> if (!lastWasCR) '\n'.code else {
            lastWasCR = false
            next()
        }

        else -> {
            lastWasCR = false
            code
        }
    }
}