package dev.kikugie.stitcher.replacement

interface ReplacementExecutor {
    val identifiers: Set<String>

    fun collect(text: CharSequence, queue: EmitQueue)

    fun replace(text: CharSequence): StringBuilder = StringBuilder(text.length).apply {
        val queue = EmitQueue()
        collect(text, queue)

        var lastEmitEnd = 0
        for (emit in queue.entries) {
            if (emit.start > lastEmitEnd) append(text, lastEmitEnd, emit.start)
            emit.appendTo(this)
            lastEmitEnd = emit.end
        }

        if (lastEmitEnd < text.length)
            append(text, lastEmitEnd, text.length)
    }
}
