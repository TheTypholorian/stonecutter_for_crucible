package dev.kikugie.stitcher.replacement.search

import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import dev.kikugie.stitcher.util.buildString
import java.util.regex.MatchResult

@JvmInline
value class RegexSearcher<T : SearchData>(val payloads: MutableList<Payload<T>> = mutableListOf()) : PatternSearcher<T> {
    data class Payload<T>(val regex: Regex, val data: T) {
        lateinit var match: MatchResult internal set
    }

    @Suppress("UNCHECKED_CAST")
    override fun search(text: CharSequence, consumer: EmitConsumer<T>) {
        for (payload in payloads) process(payload, text, consumer)
    }

    private fun process(payload: Payload<T>, text: CharSequence, consumer: EmitConsumer<T>) {
        val matcher = payload.regex.toPattern().matcher(text)
        var index = 0
        while (index >= 0 && matcher.find(index)) {
            consumer(RegexEmit(payload.data, matcher.toMatchResult()))
            index = matcher.nextIndex(text)
        }
    }

    private fun MatchResult.nextIndex(text: CharSequence): Int {
        val next = end() + if (end() == start()) 1 else 0
        return if (next <= text.length) next else -1
    }

    private data class RegexEmit<T : SearchData>(override val data: T, val match: MatchResult) : SearchEmit<T> {
        override val start: Int get() = match.start()
        override val end: Int get() = match.end()

        override fun apply(builder: StringBuilder, offset: Int): Int {
            val value = process()
            builder.replace(start + offset, end + offset, value)
            return value.length - this.length
        }

        private fun process(): String = buildString(data.value) {
            var offset = 0
            for (place in data.value.findInsertionPlaces())
                offset += place.apply(this, offset, match::group)
        }
    }
}