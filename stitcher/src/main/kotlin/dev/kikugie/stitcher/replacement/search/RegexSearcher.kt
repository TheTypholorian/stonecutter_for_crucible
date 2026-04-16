package dev.kikugie.stitcher.replacement.search

import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import dev.kikugie.stitcher.util.buildString

@JvmInline
value class RegexSearcher<T>(val payloads: MutableList<Payload<T>>) : PatternSearcher<RegexSearcher.Context, T> {
    @Suppress("UNCHECKED_CAST")
    override fun search(text: CharSequence, consumer: EmitConsumer<Context, T>) {
        for ((regex, data) in payloads) for (match in regex.findAll(text))
            consumer.accept(Context(match), data)
    }

    data class Context(val match: MatchResult) : EmitContext {
        override val start: Int get() = match.range.first
        override val end: Int get() = match.range.last + 1
    }

    data class Payload<T>(val regex: Regex, val data: T)

//    private data class RegexEmit<T : SearchData>(override val data: T, val match: MatchResult) : SearchEmit<T> {
//        override val start: Int get() = match.range.first
//        override val end: Int get() = match.range.last + 1
//
//        override fun apply(builder: StringBuilder, offset: Int): Int {
//            val value = process()
//            builder.replace(start + offset, end + offset, value)
//            return value.length - this.length
//        }
//
//        private fun process(): String = buildString(data.value) {
//            var offset = 0
//            val groups = match.groups
//            for (place in data.value.findInsertionPlaces())
//                offset += place.apply(this, offset) { groups[it]?.value }
//        }
//    }
}