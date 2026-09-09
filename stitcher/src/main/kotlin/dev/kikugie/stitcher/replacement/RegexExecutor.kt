package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.issue.REPLACEMENT_GROUP
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stonecutter.model.process.RegexReplacement
import dev.kikugie.stonecutter.model.process.Replacement
import java.util.regex.Matcher
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
            process(tracker, regex, repl.target, text, queue)
    }

    private fun process(tracker: ModificationTracker, regex: Pattern, target: String, text: String, queue: EmitQueue) {
        val matcher: Matcher = regex.matcher(text)
        val buffer = StringBuilder()
        var appended = 0
        while (matcher.find()) {
            buffer.clear()
            val prefix = matcher.start() - appended
            if (performReplacement(tracker, matcher, target, buffer)) break
            queue += RegexEmit(matcher.start(), matcher.end(), buffer.substring(prefix))
            appended = matcher.end()
        }
    }

    private fun performReplacement(tracker: ModificationTracker, matcher: Matcher, target: String, buffer: StringBuilder): Boolean = try {
        matcher.appendReplacement(buffer, target)
        false
    } catch (e: RuntimeException) {
        tracker.reportFailedReplacement(matcher.start(), target, e)
        true
    }

    private class RegexEmit(override val start: Int, override val end: Int, val value: String) : EmitQueue.Entry {
        override fun applyTo(tracker: ModificationTracker) {
            tracker.replace(tracker.start + start, tracker.start + end, value)
        }
    }
}

private fun ModificationTracker.reportFailedReplacement(offset: Int, target: String, cause: RuntimeException): Unit = document.report(
    REPLACEMENT_GROUP + "regex-error"
) {
    at = document.reference(start + offset)
    label = "Regex substitution error"
    info = "Failed to expand '$target' for the match at the reported location"
    error = cause
}
