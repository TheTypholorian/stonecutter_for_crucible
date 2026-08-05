package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stonecutter.model.process.RegexReplacement
import dev.kikugie.stonecutter.model.process.Replacement
import java.util.regex.MatchResult
import java.util.regex.Pattern

class RegexExecutor(replacements: List<Replacement>, override val identifiers: Set<String>) : ReplacementExecutor {
    private val replacements: List<Pair<Pattern, RegexReplacement>> = replacements.asSequence()
        .filterIsInstance<RegexReplacement>()
        .map { Pattern.compile(it.pattern, it.flags) to it }
        .toList()

    override fun isEmpty(): Boolean =
        replacements.isEmpty()

    override fun collect(tracker: ModificationTracker, text: String, queue: EmitQueue) {
        if (replacements.isEmpty()) return
        for ((regex, repl) in replacements) if (repl.isActive)
            process(regex, repl.target, text, queue)
    }

    private fun process(regex: Pattern, target: String, text: String, queue: EmitQueue) {
        val matcher = regex.matcher(text)
        var index = 0
        while (index >= 0 && matcher.find(index)) {
            queue += RegexEmit(matcher.toMatchResult(), target)
            index = matcher.nextIndex(text)
        }
    }

    private class RegexEmit(val match: MatchResult, val value: String) : EmitQueue.Entry {
        override val start: Int get() = match.start()
        override val end: Int get() = match.end()

        override fun applyTo(tracker: ModificationTracker) {
            tracker.replace(tracker.start + start, tracker.start + end, value)
        }
    }
}
