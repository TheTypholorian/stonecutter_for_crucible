package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stonecutter.model.process.Replacement
import dev.kikugie.stonecutter.model.process.StringReplacement

class CompositeExecutor(replacements: List<Replacement>) : ReplacementExecutor {
    override val identifiers: MutableSet<String> = HashSet()
    private val stringExecutor: StringExecutor = StringExecutor(replacements, identifiers)
    private val regexExecutor: RegexExecutor = RegexExecutor(replacements, identifiers)
    private val perlExecutor: PerlExecutor = PerlExecutor(replacements, identifiers)

    init {
        for (repl in replacements) repl.identifier.let {
            if (it != null && repl.defaultEnabled) identifiers += it
        }
    }

    fun toggle(enable: Set<String> = emptySet(), disable: Set<String> = emptySet()) {
        identifiers += enable
        identifiers -= disable
        stringExecutor.clear()
    }

    operator fun plusAssign(replacements: Sequence<Replacement>) {
        stringExecutor += replacements.filterIsInstance<StringReplacement>()
    }

    override fun isEmpty(): Boolean =
        stringExecutor.isEmpty() && regexExecutor.isEmpty() && perlExecutor.isEmpty()

    override fun collect(tracker: ModificationTracker, text: String, queue: EmitQueue) {
        stringExecutor.collect(tracker, text, queue)
        regexExecutor.collect(tracker, text, queue)
        perlExecutor.collect(tracker, text, queue)
    }
}
