package dev.kikugie.stitcher.util

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
open class NonOverlappingList<T : NonOverlappingList.Entry>(private val entries: MutableList<T>) : MutableList<T> by entries {
    interface Entry {
        val start: Int
        val end: Int

        companion object {
            val Entry.length: Int
                inline get() = end - start
        }
    }

    protected open fun onOverlap(prev: T, next: T): T = when(val it = next.start compareTo prev.start) {
        -1 -> next
        1 -> prev
        0 -> if (next.end >= prev.end) next else prev
        else -> error("Comparison value $it !in -1..1")
    }

    override fun addAll(elements: Collection<T>): Boolean =
        elements.fold(false) { bl, it -> add(it) || bl }

    override fun add(element: T): Boolean {
        require(element.start <= element.end) { "Element start ${element.start} is greater than its end ${element.end}" }
        return if (entries.isEmpty()) entries.add(element) else doAdd(element, entries.first(), entries.last())
    }

    private fun doAdd(element: T, first: T, last: T): Boolean = when {
        element.start >= last.end -> entries.add(element)
        element.end <= first.start -> entries.addFirst(element).let { true }
        element.start >= average(first.start, last.start) -> addBackwards(element)
        else -> addForwards(element)
    }

    private tailrec fun addForwards(target: T, iterator: MutableListIterator<T> = entries.listIterator()): Boolean {
        for (element in iterator) {
            // This element can be skipped if it's entirely before the target
            if (target.start >= element.end)
                continue

            // The target can be safely added before this element
            // if it's before it and all the following ones
            if (target.end <= element.start) {
                iterator.previous()
                break
            }

            val result = onOverlap(element, target)

            // Don't insert the target if it was rejected by the overlapping strategy
            if (result == element)
                return false

            iterator.remove()

            // The new target may overlap with the previous element, so we start again
            return if (result.start < element.start) addForwards(result)
            else addForwards(result, iterator)
        }

        iterator.add(target)
        return true
    }

    private fun addBackwards(target: T, iterator: MutableListIterator<T> = entries.listIterator(entries.size)): Boolean {
        while (iterator.hasPrevious()) {
            val element = iterator.previous()

            // Find the spot to start iterating forwards
            if (element.start <= target.start)
                return addForwards(target, iterator)
        }

        iterator.add(target)
        return true
    }

    // Overflow-proof average
    private fun average(a: Int, b: Int): Int =
        (a and b) + ((a xor b) shr 1)
}
