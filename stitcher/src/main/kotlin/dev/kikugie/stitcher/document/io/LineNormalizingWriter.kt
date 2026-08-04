package dev.kikugie.stitcher.document.io

import java.io.Writer

/** A [Writer] implementation that converts line breaks to the [separator] ([System.lineSeparator] by default). */
class LineNormalizingWriter(private val destination: Writer, separator: String = System.lineSeparator(), lock: Any = destination) : Writer(lock) {
    private val separators: CharArray = separator.toCharArray()
    private var lastWasCR: Boolean = false

    override fun flush(): Unit = destination.flush()
    override fun close(): Unit = destination.flush()

    override fun write(cbuf: CharArray?, off: Int, len: Int) {
        if (cbuf == null || len == 0) return

        val end = off + len
        var checkpoint = -1
        for (i in off until end) when (cbuf[i]) {
            '\r' -> {
                if (checkpoint >= 0) destination.write(cbuf, checkpoint, i - checkpoint)
                destination.write(separators)
                lastWasCR = true
                checkpoint = -1
            }

            '\n' -> {
                if (checkpoint >= 0) destination.write(cbuf, checkpoint, i - checkpoint)
                if (!lastWasCR) destination.write(separators)
                lastWasCR = false
                checkpoint = -1
            }

            else -> {
                lastWasCR = false
                if (checkpoint < 0) checkpoint = i
            }
        }

        if (checkpoint >= 0) destination.write(cbuf, checkpoint, end - checkpoint)
    }
}