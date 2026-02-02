package dev.kikugie.stitcher.replacement.search

import java.util.LinkedList

typealias EmitConsumer<T> = (emit: SearchEmit<T>) -> Unit

/**
 * Searches for pattern matches in text and reports findings through a consumer.
 */
interface PatternSearcher<T : SearchData> {
    fun search(text: CharSequence, consumer: EmitConsumer<T>)
}

interface SearchData {
    val value: String
}

interface SearchEmit<T : SearchData> {
    val data: T
    val start: Int
    val end: Int

    fun apply(builder: StringBuilder, offset: Int): Int
}

val SearchEmit<*>.length: Int
    inline get() = end - start

/**
 * A specialized queue for [SearchEmit] elements that maintains non-overlapping emits sorted by position.
 *
 * Added emits are processed with the following rules:
 * 1. Earlier emits have priority
 * ```
 * | --ABC---
 * + ---DEFG-
 * = --ABC---
 * ```
 * 2. Longer emits have priority
 * ```
 * | --ABC---
 * + --ABCD--
 * = --ABCD--
 * ```
 * 3. Newer emits have priority
 * ```
 * | --ABC---
 * + --CDE---
 * = --CDE---
 * ```
 */
@JvmInline @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
value class SearchEmitQueue<T : SearchData> private constructor(private val list: LinkedList<SearchEmit<T>>) : MutableCollection<SearchEmit<T>> by list {
    constructor() : this(LinkedList())

    /**
     * Adds all elements from the collection to the queue, applying overlap and length filtering for each.
     *
     * @param elements the collection of emits to add
     * @return true if at least one emit was added successfully, false if all were rejected
     */
    override fun addAll(elements: Collection<SearchEmit<T>>): Boolean {
        var added = false
        for (element in elements)
            added = add(element) || added
        return added
    }

    /**
     * Adds an emit to the queue, removing any overlapping or shorter emits.
     *
     * Delegates to [addFromEnd] if the queue is empty or the element ends at or after the last element,
     * otherwise delegates to [addFromStart].
     *
     * @param element the emit to add
     * @return true if the emit was added successfully, false if it was rejected due to overlap or being shorter
     */
    override fun add(element: SearchEmit<T>): Boolean =
        if (list.isEmpty() || element.end >= list.peekLast().end) addFromEnd(element)
        else addFromStart(element)

    /**
     * Attempts to add an emit to the end of the queue, removing overlapping or shorter emits.
     *
     * Removes emits from the end of the queue that either start at the same position with shorter or equal length,
     * start before and overlap with the new emit, or start after the new emit.
     *
     * @param emit the emit to add
     * @return true if the emit was added successfully, false if it was rejected due to overlap or being shorter
     */
    private fun addFromEnd(emit: SearchEmit<T>): Boolean {
        while (list.isNotEmpty()) {
            val last = list.peekLast()
            when {
                last.start == emit.start ->
                    if (last.length > emit.length) return false
                    else list.removeLast()

                last.start < emit.start ->
                    if (last.end > emit.start) return false
                    else break

                else -> list.removeLast()
            }
        }
        return list.offer(emit)
    }

    /**
     * Attempts to add an emit to the queue at its correct position based on start index, removing overlapping or shorter emits.
     *
     * Iterates from the beginning of the queue and removes emits that either start at the same position with shorter or equal length,
     * or overlap with the new emit. Rejects the new emit if an existing emit with the same start position is longer,
     * or if an existing emit ends after the new emit's start position.
     *
     * @param emit the emit to add
     * @return true if the emit was added successfully, false if it was rejected due to overlap or being shorter
     */
    private fun addFromStart(emit: SearchEmit<T>): Boolean {
        val iterator = list.listIterator()
        for (existing in iterator) when {
            existing.start < emit.start ->
                if (existing.end > emit.start) return false

            existing.start == emit.start ->
                if (existing.length > emit.length) return false
                else iterator.remove()

            else ->
                if (emit.end > existing.start) iterator.remove()
                else {
                    iterator.previous()
                    iterator.add(emit)
                    return true
                }
        }
        iterator.add(emit)
        return true
    }
}