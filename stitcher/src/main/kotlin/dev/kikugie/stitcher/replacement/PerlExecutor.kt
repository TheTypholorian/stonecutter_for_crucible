package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.issue.REPLACEMENT_GROUP
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stonecutter.model.process.PerlReplacement
import dev.kikugie.stonecutter.model.process.Replacement
import org.pcre4j.Pcre2Code
import org.pcre4j.Pcre2MatchData
import org.pcre4j.option.Pcre2CompileOption
import org.pcre4j.option.Pcre2CompileOption.UTF
import org.pcre4j.option.Pcre2MatchOption
import org.pcre4j.option.Pcre2SubstituteOption
import org.pcre4j.option.Pcre2SubstituteOption.EXTENDED
import org.pcre4j.option.Pcre2SubstituteOption.REPLACEMENT_ONLY
import java.util.*

private val FLAGS: EnumSet<Pcre2SubstituteOption> = EnumSet.of(EXTENDED, REPLACEMENT_ONLY)
private val MATCH_FLAGS: EnumSet<Pcre2MatchOption> = EnumSet.noneOf(Pcre2MatchOption::class.java)

class PerlExecutor(replacements: List<Replacement>, override val identifiers: Set<String>) : ReplacementExecutor {
    private val replacements: List<Pair<Pcre2Code, PerlReplacement>> = replacements.asSequence()
        .filterIsInstance<PerlReplacement>()
        .map { Pcre2Code(it.pattern, it.compileOptions()) to it }
        .toList()

    override fun isEmpty(): Boolean =
        replacements.isEmpty()

    override fun collect(tracker: ModificationTracker, text: String, queue: EmitQueue) {
        if (replacements.isEmpty()) return
        val cursor = Utf8Cursor(text)
        for ((pattern, repl) in replacements) if (repl.isActive)
            process(tracker, pattern, repl.target, text, queue, cursor)
    }

    private fun process(tracker: ModificationTracker, pattern: Pcre2Code, target: String, text: String, queue: EmitQueue, cursor: Utf8Cursor) {
        val data = Pcre2MatchData(pattern)
        var start = 0
        while (start in text.indices) {
            // `substitute` reports no match by returning an empty result and leaving the previous match in the `ovector`,
            // so it needs to be matched separately.
            if (pattern.match(text, start, MATCH_FLAGS, data, null) < 0) break

            val value = try {
                pattern.substitute(text, start, FLAGS, data, null, target)
            } catch (e: RuntimeException) {
                tracker.reportFailedReplacement(start, e)
                break
            }

            val offsets = data.ovector()
            val from = cursor.indexOf(offsets[0])
            val to = cursor.indexOf(offsets[1])
            queue += PerlEmit(from, to, value)
            start = if (to == from) to + 1 else to
        }
    }

    private class PerlEmit(override val start: Int, override val end: Int, val value: String) : EmitQueue.Entry {
        override fun applyTo(tracker: ModificationTracker) {
            tracker.replace(tracker.start + start, tracker.start + end, value)
        }
    }
}

/**
 * Translates PCRE2's UTF-8 byte offsets back to character indices in [text].
 */
private class Utf8Cursor(private val text: String) {
    private val ascii: Boolean = text.all { it.code <= 0x7F }
    private var offset: Int = 0
    private var index: Int = 0

    fun indexOf(bytes: Long): Int {
        if (ascii) return bytes.toInt()
        while (offset > bytes && index > 0) offset -= text[--index].utf8Length()
        while (offset < bytes && index < text.length) offset += text[index++].utf8Length()
        return index
    }
}

private fun Char.utf8Length(): Int = when {
    this <= '\u007F' -> 1
    this <= '\u07FF' -> 2
    Character.isSurrogate(this) -> 2
    else -> 3
}

private fun PerlReplacement.compileOptions(): EnumSet<Pcre2CompileOption> =
    flags.toEnumSet<Pcre2CompileOption>().apply { add(UTF) }

private fun ModificationTracker.reportFailedReplacement(offset: Int, cause: RuntimeException): Unit = document.report(
    REPLACEMENT_GROUP + "pcre-error"
) {
    at = document.reference(start + offset)
    label = "PCRE substitution error"
    info = "Pcre4j failed somewhere after the reported location"
    error = cause
}
