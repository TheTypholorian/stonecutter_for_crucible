package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.ProblemReport
import dev.kikugie.stitcher.issue.ProblemReport.Companion.create

private val REPL_GROUP: ProblemID = ProblemID("replacement-builder")

private const val IDENTIFIER_PATTERN: String = "[_a-zA-Z][_\\-a-zA-Z0-9]*"
private val IDENTIFIER_REGEX: Regex = Regex(IDENTIFIER_PATTERN)

interface ReplacementBuilder<T : Replacement> {
    fun add(replacement: T): ProblemReport?
    fun build(): List<T>

    companion object {
        operator fun invoke(entries: Iterable<Replacement> = emptyList()): ReplacementBuilder<Replacement> =
            CompositeReplacementBuilder(entries)

        fun string(entries: Iterable<StringReplacement> = emptyList()): ReplacementBuilder<StringReplacement> =
            StringReplacementBuilder(entries)

        fun regex(entries: Iterable<RegexReplacement> = emptyList()): ReplacementBuilder<RegexReplacement> =
            RegexReplacementBuilder(entries)
    }
}

private class CompositeReplacementBuilder(entries: Iterable<Replacement>) : ReplacementBuilder<Replacement> {
    private val regexBuilder: RegexReplacementBuilder = RegexReplacementBuilder(entries.filterIsInstance<RegexReplacement>())
    private val stringBuilder: StringReplacementBuilder = StringReplacementBuilder(entries.filterIsInstance<StringReplacement>())

    override fun build(): List<Replacement> = stringBuilder.build() + regexBuilder.build()

    override fun add(replacement: Replacement) = when (replacement) {
        is StringReplacement -> stringBuilder.add(replacement)
        is RegexReplacement -> regexBuilder.add(replacement)
    }
}

private class StringReplacementBuilder() : ReplacementBuilder<StringReplacement> {
    constructor(entries: Iterable<StringReplacement>) : this() {
        for (it in entries) stubs += StringReplacementStub(it.target, it.sources.toMutableSet(), it.identifier, it.tags)
    }

    private val stubs: MutableList<StringReplacementStub> = mutableListOf()

    override fun build(): List<StringReplacement> = stubs.map(StringReplacementStub::build)

    override fun add(replacement: StringReplacement): ProblemReport? = with(replacement) {
        if (sources.any(String::isEmpty)) return invalidSourceReport()
        if (target.isEmpty()) return invalidTargetReport()
        if (identifier != null && !IDENTIFIER_REGEX.matches(identifier)) return invalidIdentifierReport(identifier)
        for (source in sources) merge(target, source, identifier, replacement.tags)
            .let { if (it != null) return it }
        null
    }

    private fun merge(target: String, source: String, identifier: String?, tags: Set<Replacement.Tag>): ProblemReport? {
        val matching = stubs.asSequence().filter {
            it.identifier == identifier && it.tags.size == tags.size && it.tags.containsAll(tags)
        }
        if (matching.none {
            val (result, report) = it.tryMerge(target, source)
            if (report != null) return report else result
        }) stubs += StringReplacementStub(target, source, identifier, tags)
        return null
    }

    private fun StringReplacementStub.tryMerge(newTarget: String, newSource: String): Pair<Boolean, ProblemReport?> = when {
        // Transitive replacement: ({a, b} -> c) + ({c} -> d) = ({a, b, c} -> d)
        newSource == target -> if (newTarget in sources) false to cyclicalReplacementReport(this, newSource, newTarget) else {
            sources += newSource
            target = newTarget
            true to null
        }

        // Composite replacement: ({a, b} -> d) + ({c} -> d) = ({a, b, c} -> d)
        // Transitive replacement: ({a, c} -> d) + ({b} -> c) = ({a, b, c} -> d)
        newTarget == target || newTarget in sources -> {
            sources += newSource; true to null
        }

        // Ambiguous replacement: ({a, b} -> d) + ({a, c} -> d)
        else -> if (newSource in sources) false to ambiguousReplacementReport(this, newSource, newTarget) else false to null
    }

    private fun descriptor(target: String, sources: Iterable<String>): String =
        "${sources.joinToString(", ", "[", "]") { "'$it'" }} -> '$target'"

    private fun cyclicalReplacementReport(stub: StringReplacementStub, source: String, target: String) = create(REPL_GROUP.child("cyclical-string")) {
        label = "Cyclical replacement"
        details = """
            When trying to merge replacements it forms a cyclic replacement, which is not allowed to guarantee order-independence:
            ${descriptor(stub.target, stub.sources)}
              + ${descriptor(target, listOf(source))}
              ? '$source' -> '$target' -> '$source'
        """.trimIndent()
        docs = "https://stonecutter.kikugie.dev/wiki/config/params#string-replacements"
    }

    private fun ambiguousReplacementReport(stub: StringReplacementStub, source: String, target: String) = create(REPL_GROUP.child("ambiguous-string")) {
        label = "Ambiguous replacement"
        details = """
            When trying to merge replacements it encounters two different substitutions, which is not allowed to guarantee order-independence:
            ${descriptor(stub.target, stub.sources)}
              + ${descriptor(target, listOf(source))}
              ? '$source' -> '${stub.target}' & '$target'
        """.trimIndent()
        docs = "https://stonecutter.kikugie.dev/wiki/config/params#string-replacements"
    }

    private data class StringReplacementStub(
        var target: String,
        val sources: MutableSet<String>,
        val identifier: String?,
        val tags: Set<Replacement.Tag>
    ) {
        constructor(target: String, source: String, identifier: String? = null, tags: Set<Replacement.Tag> = emptySet()) : this(
            target,
            mutableSetOf(source),
            identifier,
            tags
        )

        fun build(): StringReplacement = StringReplacement(target, sources, identifier, tags)
    }
}

private class RegexReplacementBuilder() : ReplacementBuilder<RegexReplacement> {
    constructor(entries: Iterable<RegexReplacement>) : this() {
        replacements.addAll(entries)
    }

    private val replacements: MutableList<RegexReplacement> = mutableListOf()

    override fun build(): List<RegexReplacement> = replacements.toList()

    override fun add(replacement: RegexReplacement): ProblemReport? = with(replacement) {
        if (pattern.isEmpty()) return invalidSourceReport()
        if (target.isEmpty()) return invalidTargetReport()
        if (identifier != null && !IDENTIFIER_REGEX.matches(identifier)) return invalidIdentifierReport(identifier)
        replacements += this
        null
    }
}

private fun invalidSourceReport(): ProblemReport = create(REPL_GROUP.child("empty-source")) {
    label = "Cannot replace an empty string"
    details = "An empty string can't be located in a file, therefore it can't be replaced."
}

private fun invalidTargetReport(): ProblemReport = create(REPL_GROUP.child("empty-target")) {
    label = "Cannot substitute an empty string"
    details = "Substituting an empty string is an irreversible operation, which is not allowed."
}

private fun invalidIdentifierReport(id: String): ProblemReport = create(REPL_GROUP.child("invalid-id")) {
    label = "Invalid replacement identifier"
    details = "Replacement identifier '$id' doesn't match `$IDENTIFIER_REGEX` pattern."
}
