package dev.kikugie.stitcher.replacement.search

/**
 * A simple pattern searcher that performs brute-force substring matching for multiple keywords.
 *
 * This matcher has the worst-case performance of `O(len * pat)`, but unlike [TrieSearcher] its
 * patterns set can be modified dynamically, and it has no setup cost.
 */
@JvmInline
value class NativeSearcher<T : SearchData>(val payloads: MutableList<Payload<T>> = mutableListOf()) : PatternSearcher<T> {
    override fun search(text: CharSequence, consumer: EmitConsumer<T>) {
        for (payload in payloads) {
            search(text, payload.keyword, payload.data, consumer)
            if (payload.isSelfContaining) search(text, payload.data.value, payload.data, consumer)
        }
    }

    private fun search(text: CharSequence, keyword: String, data: T, consumer: EmitConsumer<T>) {
        var index = 0
        while (text.indexOf(keyword, index).also { index = it } >= 0) {
            consumer(NativeEmit(data, index, index + keyword.length))

            // Matches already have to be non-overlapping, so jumping over it is fine
            index += keyword.length
        }
    }

    data class Payload<T : SearchData>(val keyword: String, val data: T) {
        // When it's already a preserving replacement, don't search for it twice
        val isSelfContaining: Boolean = keyword != data.value && keyword in data.value
    }

    private data class NativeEmit<T : SearchData>(override val data: T, override val start: Int, override val end: Int) : SearchEmit<T> {
        override fun apply(builder: StringBuilder, offset: Int): Int {
            builder.replace(start + offset, end + offset, data.value)
            return data.value.length - this.length
        }
    }
}