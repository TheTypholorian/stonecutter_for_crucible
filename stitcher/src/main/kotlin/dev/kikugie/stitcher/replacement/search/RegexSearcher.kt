package dev.kikugie.stitcher.replacement.search

import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import dev.kikugie.stitcher.util.buildString

@JvmInline
value class RegexSearcher<T : SearchData>(val payloads: MutableList<Payload<T>> = mutableListOf()) : PatternSearcher<T> {
    data class Payload<T>(val regex: Regex, val data: T) {
        lateinit var match: MatchResult internal set
    }

    @Suppress("UNCHECKED_CAST")
    override fun search(text: CharSequence, consumer: EmitConsumer<T>) {
        for ((regex, data) in payloads) for (match in regex.findAll(text))
            consumer(RegexEmit(data, match))
    }

    private data class RegexEmit<T : SearchData>(override val data: T, val match: MatchResult) : SearchEmit<T> {
        override val start: Int get() = match.range.first
        override val end: Int get() = match.range.last + 1

        override fun apply(builder: StringBuilder, offset: Int): Int {
            val value = process()
            builder.replace(start + offset, end + offset, value)
            return value.length - this.length
        }

        private fun process(): String = buildString(data.value) {
            var offset = 0
            val groups = match.groups
            for (place in data.value.findInsertionPlaces())
                offset += place.apply(this, offset) { groups[it]?.value }
        }
    }
}