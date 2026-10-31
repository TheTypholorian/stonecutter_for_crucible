package dev.kikugie.stitcher.document.io

import java.io.Reader

/** A [Reader] implementation that converts any platform line separator to `\n`. */
class LineNormalizingReader(private val source: Reader, lock: Any = source) : Reader(lock) {
    private var lastWasCR: Boolean = false

    /** The first line separator read from the [source], or `null` if it has not been read yet. */
    var separator: String? = null
        private set

    override fun close(): Unit = source.close()

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (len == 0) return 0

        for (i in 0 until len) when (val code = next()) {
            -1 -> return if (i == 0) -1 else i
            else -> cbuf[off + i] = code.toChar()
        }
        return len
    }

    private tailrec fun next(): Int {
        val code = source.read()
        if (lastWasCR && code != '\n'.code)
            separator = separator ?: "\r"

        return when (code) {
            -1 -> -1

            '\r'.code -> {
                lastWasCR = true
                '\n'.code
            }

            '\n'.code -> if (!lastWasCR) '\n'.code.also { separator = separator ?: "\n" } else {
                lastWasCR = false
                separator = separator ?: "\r\n"
                next()
            }

            else -> {
                lastWasCR = false
                code
            }
        }
    }
}