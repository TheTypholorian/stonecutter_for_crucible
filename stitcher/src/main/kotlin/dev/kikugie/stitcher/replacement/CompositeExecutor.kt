package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stonecutter.model.process.Replacement
import dev.kikugie.stonecutter.model.process.ReplacementModel

class CompositeExecutor(replacements: List<ReplacementModel>) : ReplacementExecutor {
    private val switches: MutableList<ReplacementState<*>> =
        replacements.mapTo(ArrayList()) { ReplacementState(it.replacement, it.enabled) }
    private val stringExecutor: StringExecutor = StringExecutor(switches.ofType())
    private val regexExecutor: RegexExecutor = RegexExecutor(switches.ofType())
    private val perlExecutor: PerlExecutor = PerlExecutor(switches.ofType())

    fun toggle(enable: Set<String> = emptySet(), disable: Set<String> = emptySet()) {
        for (it in switches) {
            val id = it.value.identifier ?: continue
            if (id in enable) it.enabled = true
            else if (id in disable) it.enabled = false
        }
        stringExecutor.clear()
    }

    operator fun plusAssign(replacements: Sequence<Replacement>) {
        val added = replacements.map { ReplacementState(it, false) }.toList()
        switches += added
        stringExecutor += added.ofType()
    }

    override fun isEmpty(): Boolean =
        stringExecutor.isEmpty() && regexExecutor.isEmpty() && perlExecutor.isEmpty()

    override fun collect(tracker: ModificationTracker, text: String, queue: EmitQueue) {
        stringExecutor.collect(tracker, text, queue)
        regexExecutor.collect(tracker, text, queue)
        perlExecutor.collect(tracker, text, queue)
    }
}
