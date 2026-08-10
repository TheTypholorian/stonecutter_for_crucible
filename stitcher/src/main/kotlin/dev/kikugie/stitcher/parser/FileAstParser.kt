@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.parser

import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.issue.LAYOUT_GROUP
import dev.kikugie.stitcher.document.issue.ProblemReport.Severity
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.token.*
import dev.kikugie.stitcher.token.DefinitionToken.Kind.*
import dev.kikugie.stitcher.util.*

object FileAstParser {
    fun parse(stream: FileTokenStream): BlockToken.Root {
        val builder = RootScopeBuilder().apply { accept(stream) }
        return BlockToken.Root(stream.document, ArrayList<BlockToken>().apply {
            builder.collect(this)
        })
    }
}

private interface FileAstBuilder {
    fun accept(stream: FileTokenStream)
    fun collect(list: MutableList<BlockToken>)
}

private class ContentBuilder(val token: BlockToken.Content) : FileAstBuilder {
    val text: String inline get() = token.ref.text

    override fun accept(stream: FileTokenStream): Unit = Unit
    override fun collect(list: MutableList<BlockToken>): Unit = list.plusAssign(token)
}

private class CommentBuilder(val token: BlockToken.Comment) : FileAstBuilder {
    val text: String inline get() = token.body.ref.text

    override fun accept(stream: FileTokenStream): Unit = Unit
    override fun collect(list: MutableList<BlockToken>): Unit = list.plusAssign(token)
}

private open class ScopeBuilder : FileAstBuilder {
    val entries: MutableList<FileAstBuilder> = ArrayList()
    var scopeIsNotBlank: Boolean = false

    override fun accept(stream: FileTokenStream) {
        while (true) this += when (val next: BlockToken = stream.peek(1) ?: break) {
            is BlockToken.Content -> processContent(next, stream)
            is BlockToken.Comment -> processComment(next, stream)
            is BlockToken.Code -> processCode(next, stream)
            is BlockToken.Root -> continue
        } ?: break
    }

    override fun collect(list: MutableList<BlockToken>) {
        for (it in entries) it.collect(list)
    }

    protected operator fun plusAssign(builder: FileAstBuilder) {
        entries += builder
        scopeIsNotBlank = scopeIsNotBlank || builder.isNotBlank()
    }

    // Create and process a new [FileAstBuilder] from the [token], or null if this scope can't accept any more.
    // It's the responsibility of the implementation to advance the [stream] if the result is non-null.
    protected open fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? {
        return newCodeScopeBuilder(token).apply { accept(stream.advance()) }
    }

    protected open fun processContent(token: BlockToken.Content, stream: FileTokenStream): FileAstBuilder? =
        ContentBuilder(token).also { stream.consume() }

    protected open fun processComment(token: BlockToken.Comment, stream: FileTokenStream): FileAstBuilder? =
        CommentBuilder(token).also { stream.consume() }
}

// The file's top-level scope. A closer reaching here terminates nothing, so it's reported as unmatched.
private class RootScopeBuilder : ScopeBuilder() {
    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? {
        if (token.definition.kind == CLOSER) reportRootCloser(token)
        return super.processCode(token, stream)
    }
}

private abstract class CodeScopeBuilder(val token: BlockToken.Code, val parent: ScopeBuilder) : ScopeBuilder() {
    var child: CodeScopeBuilder? = null
    var foundSplitMatch: Boolean = false

    override fun collect(list: MutableList<BlockToken>) {
        list += token.copy(scope = ArrayList<BlockToken>().apply {
            for (it in entries) it.collect(this)
        })
        child?.collect(list)
    }

    open fun isTerminatedBy(block: BlockToken.Code): Boolean = when {
        token.marker neqtype block.marker -> false
        token.definition.kind.isScoped -> block.definition.kind.isCloser
        token.definition.kind.isOpen -> block.definition.kind.isExtension
        else -> false
    }

    protected open fun processSplitContent(token: BlockToken.Content, stream: FileTokenStream, split: Int): FileAstBuilder? = when (split) {
        // Match found exactly at the start of the block, so it's excluded
        0 -> null

        // Match not foun, so try searching next
        -1 -> super.processContent(token, stream)

        // Match found, capturing the entire token
        token.leaf.length -> {
            foundSplitMatch = true
            super.processContent(token, stream)
        }

        // Match found in the middle, so return the consumed part and insert the remainder
        else -> with(token) {
            foundSplitMatch = true
            val first = BlockToken.Content(leaf.withRef { copy(end = start + split) })
            val second = BlockToken.Content(leaf.withRef { copy(start = start + split) })
            super.processContent(first, stream).also { stream.insert(second) }
        }
    }

    protected open fun processSplitComment(token: BlockToken.Comment, stream: FileTokenStream, split: Int): FileAstBuilder? = when (split) {
        // Match found exactly at the start of the block, so it's excluded
        0 -> null

        // Match not foun, so try searching next
        -1 -> super.processComment(token, stream)

        // Match found, capturing the entire token
        token.body.length -> {
            foundSplitMatch = true
            super.processComment(token, stream)
        }

        // Match found in the middle, so return the consumed part and insert the remainder
        else -> with(token) {
            foundSplitMatch = true
            val first = copy(body = body.withRef { copy(end = start + split) }, closer = null)
            val second = copy(body = body.withRef { copy(start = start + split) }, opener = null)
            super.processComment(first, stream).also { stream.insert(second) }
        }
    }

    protected open fun claimCodeBuilder(builder: CodeScopeBuilder, stream: FileTokenStream): Nothing? {
        child = builder.apply { accept(stream.advance()) }
        return null
    }

    // Line and lookup branches finish before their extension, which sits past a blank gap. This absorbs
    // that gap and claims the extension as the [child], continuing the condition chain.
    protected fun claimTrailingExtension(stream: FileTokenStream) {
        val distance: Int = findTrailingExtension(stream) ?: return
        repeat(distance - 1) { this += ContentBuilder(stream.peek(1) as BlockToken.Content).also { stream.consume() } }
        claimCodeBuilder(newCodeScopeBuilder(stream.peek(1) as BlockToken.Code), stream)
    }

    private fun findTrailingExtension(stream: FileTokenStream): Int? {
        var offset = 1
        while (true) when (val next: BlockToken = stream.peek(offset) ?: return null) {
            is BlockToken.Code -> return if (next.definition is DefinitionToken.ConditionExtension) offset else null
            is BlockToken.Content if next.leaf.text.isBlank() -> offset++
            else -> return null
        }
    }
}

// No-scope definitions: every closer, replacement toggles, and named local replacements.
private class EmptyCodeBuilder(token: BlockToken.Code, parent: ScopeBuilder) : CodeScopeBuilder(token, parent) {
    override fun accept(stream: FileTokenStream): Unit = Unit
    override fun collect(list: MutableList<BlockToken>): Unit = list.plusAssign(token)
}

// Scope delimited by `{ ... }`. Ends on its matching terminator (which it owns), on a foreign closer,
// or at the stream's end - the latter is reported as a missing closer.
private abstract class ScopedCodeScopeBuilder(token: BlockToken.Code, parent: ScopeBuilder) : CodeScopeBuilder(token, parent) {
    override fun accept(stream: FileTokenStream) {
        super.accept(stream)
        // A terminator (own closer, extension, or a claimed foreign closer) becomes the [child]; its absence
        // means the scope ran to the stream's end unterminated. Reported here as it needs the following tokens.
        if (child == null) reportMissingClosingBracket(token)
    }
}

// Scope covering the rest of the line, extended over leading blank lines. Content and comments are split at
// the line break; a following condition branch is pulled in across any blank gap.
private abstract class LineCodeScopeBuilder(token: BlockToken.Code, parent: ScopeBuilder) : CodeScopeBuilder(token, parent) {
    // Set when the scope proceeds past an inline nested block, so the content right after it continues the
    // same physical line and must be captured (and split at the line break), not rejected as a fresh line.
    protected var midLineContinuation: Boolean = false

    override fun accept(stream: FileTokenStream) {
        if (!foundSplitMatch) super.accept(stream)
    }

    override fun processContent(token: BlockToken.Content, stream: FileTokenStream): FileAstBuilder? {
        val freshLine: Boolean = !midLineContinuation
        midLineContinuation = false
        // Real content on a fresh line after the extended body ends the branch - it's no longer governed by
        // the condition. This also bounds a commented body (whose line breaks live in comment closers, not
        // in any content the split inspects) at the first genuine code line, e.g. a closing `}`. Content
        // continuing the same line past an inline nested block is still captured, split at the line break.
        return if (freshLine && scopeIsNotBlank && token.leaf.text.isNotBlank()) null
        else processSplitContent(token, stream, token.findLineSplit(scopeIsNotBlank))
    }

    override fun processComment(token: BlockToken.Comment, stream: FileTokenStream): FileAstBuilder? {
        midLineContinuation = false
        return processSplitComment(token, stream, token.findLineSplit(scopeIsNotBlank))
    }

    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? {
        // A definition following real content on the line starts a separate construct and is left for the
        // enclosing scope (it becomes a sibling, not a nested child). On a still-blank line it nests and owns
        // the rest of the line, as before.
        return if (scopeIsNotBlank) null else super.processCode(token, stream)
    }
}

private abstract class LookupCodeScopeBuilder(token: BlockToken.Code, parent: ScopeBuilder) : CodeScopeBuilder(token, parent) {
    protected val lookupString: String?
    protected val isCapturing: Boolean

    init {
        val opener = token.definition.opener as? ScopeToken.Lookup
        lookupString = opener?.lookup?.unquote()
        isCapturing = opener?.plus != null
    }

    override fun accept(stream: FileTokenStream) {
        if (!foundSplitMatch) super.accept(stream)
    }

    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? {
        if (scopeIsNotBlank) return null

        // The search shouldn't continue past the nested scope.
        foundSplitMatch = true
        return super.processCode(token, stream)
    }

    override fun processContent(token: BlockToken.Content, stream: FileTokenStream): FileAstBuilder? =
        // Once the match is found the scope is complete; further tokens belong to the enclosing scope. Without
        // this the remainder past the match (or a later token that happens to match again) would be re-captured.
        if (foundSplitMatch) null
        else processSplitContent(token, stream, token.findLookupSplit(lookupString, isCapturing))

    override fun processComment(token: BlockToken.Comment, stream: FileTokenStream): FileAstBuilder? =
        if (foundSplitMatch) null
        else processSplitComment(token, stream, token.findLookupSplit(lookupString, isCapturing))
}

private class ScopedSwapBuilder(token: BlockToken.Code, parent: ScopeBuilder) : ScopedCodeScopeBuilder(token, parent) {
    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? = when(token.definition) {
        is DefinitionToken.SwapCloser -> claimCodeBuilder(EmptyCodeBuilder(token, this), stream)
        else -> {
            // Code in swaps is not processed in any way as to not affect the substituted scope.
            stream.consume()
            reportSwapNesting(token)
            EmptyCodeBuilder(token, this)
        }
    }
}

private class LineSwapBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LineCodeScopeBuilder(token, parent) {
    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? {
        // Swap scopes have the least priority, so check if anything closes it.
        if (anyAncestor { it.isTerminatedBy(token) }) return null

        // Handles the cases where a code block is found inside the line scope:
        // - If it spans multiple lines, it is included in the scope entirely, and the search ends.
        //   This is the case because leaving partial code comments could cause problems.
        // - If it's a fully inline block comment (its closer keeps the line open), the search proceeds to
        //   account for the rest of the line.
        // - A line comment closer ends the line, so what follows is a fresh line and bounds the scope.
        if (token.host.findLineSplit(immediate = scopeIsNotBlank) !in -1..0) foundSplitMatch = true
        else if ('\n' !in token.host.closer?.text.orEmpty()) midLineContinuation = true

        // Code in swaps is not processed in any way as to not affect the substituted scope.
        stream.consume()
        reportSwapNesting(token)
        return EmptyCodeBuilder(token, this)
    }
}

private class LookupSwapBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LookupCodeScopeBuilder(token, parent) {
    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? {
        // Unlike the line scope, a code block is rejected in lookup swaps because of the different expectations.
        // I.e. the line scope is expected to affect the whole line, and lookup to find a string.
        return null
    }
}

private class ScopedLocalReplacementBuilder(token: BlockToken.Code, parent: ScopeBuilder) : ScopedCodeScopeBuilder(token, parent) {
    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? = when (token.definition) {
        is DefinitionToken.ReplacementCloser -> claimCodeBuilder(EmptyCodeBuilder(token, this), stream)
        else -> super.processCode(token, stream).also {
            if (token.definition.kind.isCloser) reportInvalidCloserType(this.token, token)
        }
    }
}

private class LineLocalReplacementBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LineCodeScopeBuilder(token, parent)

private class LookupLocalReplacementBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LookupCodeScopeBuilder(token, parent)

// These classes apply both to openers and extensions. Extensions are NOT to be added to [entries] but added via [claimCodeBuilder]
private class ScopedConditionBuilder(token: BlockToken.Code, parent: ScopeBuilder) : ScopedCodeScopeBuilder(token, parent) {
    // The closer and any following branch sit at the scope's end and are nested as the [child].
    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? = when (token.definition) {
        is DefinitionToken.ConditionCloser -> claimCodeBuilder(EmptyCodeBuilder(token, this), stream)
        is DefinitionToken.ConditionExtension -> claimCodeBuilder(newCodeScopeBuilder(token), stream)
        else -> super.processCode(token, stream).also {
            if (token.definition.kind.isCloser) reportInvalidCloserType(this.token, token)
        }
    }
}

private class LineConditionBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LineCodeScopeBuilder(token, parent) {
    override fun accept(stream: FileTokenStream) {
        super.accept(stream)
        if (child == null) claimTrailingExtension(stream)
    }

    // An extension directly abutting the opener (empty branch) is claimed here; otherwise [claimTrailingExtension] does.
    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? = when (token.definition) {
        is DefinitionToken.ConditionExtension -> claimCodeBuilder(newCodeScopeBuilder(token), stream)
        else -> super.processCode(token, stream)
    }
}

private class LookupConditionBuilder(token: BlockToken.Code, parent: ScopeBuilder) : LookupCodeScopeBuilder(token, parent) {
    override fun accept(stream: FileTokenStream) {
        super.accept(stream)
        if (child == null) claimTrailingExtension(stream)
    }

    override fun processCode(token: BlockToken.Code, stream: FileTokenStream): FileAstBuilder? = when (token.definition) {
        is DefinitionToken.ConditionExtension -> claimCodeBuilder(newCodeScopeBuilder(token), stream)
        else -> super.processCode(token, stream)
    }
}

private inline fun FileTokenStream.advance(): FileTokenStream =
    apply(FileTokenStream::consume)

context(parent: ScopeBuilder)
private fun newCodeScopeBuilder(token: BlockToken.Code): CodeScopeBuilder {
    val def: DefinitionToken = token.definition
    return if (def.kind.isEmpty) EmptyCodeBuilder(token, parent)
    else newNonEmptyCodeScopeBuilder(token, def)
}

context(parent: ScopeBuilder)
private fun newNonEmptyCodeScopeBuilder(token: BlockToken.Code, def: DefinitionToken): CodeScopeBuilder = when (def) {
    is DefinitionToken.ConditionOpener, is DefinitionToken.ConditionExtension -> when (def.kind) {
        LOOKUP_OPENER, LOOKUP_EXTENSION -> LookupConditionBuilder(token, parent)
        LINE_OPENER, LINE_EXTENSION -> LineConditionBuilder(token, parent)
        else -> ScopedConditionBuilder(token, parent)
    }

    is DefinitionToken.ReplacementLocal -> when (def.kind) {
        LOOKUP_OPENER, LOOKUP_EXTENSION -> LookupLocalReplacementBuilder(token, parent)
        LINE_OPENER, LINE_EXTENSION -> LineLocalReplacementBuilder(token, parent)
        else -> ScopedLocalReplacementBuilder(token, parent)
    }

    // Local swaps have the same scope requirements as regular ones.
    is DefinitionToken.SwapOpener, is DefinitionToken.SwapLocal -> when (def.kind) {
        LOOKUP_OPENER, LOOKUP_EXTENSION -> LookupSwapBuilder(token, parent)
        LINE_OPENER, LINE_EXTENSION -> LineSwapBuilder(token, parent)
        else -> ScopedSwapBuilder(token, parent)
    }

    else -> EmptyCodeBuilder(token, parent)
}

context(parent: CodeScopeBuilder)
private fun reportSwapNesting(block: BlockToken.Code): Unit = block.ref.report(LAYOUT_GROUP + "definition-in-swap-scope") {
    severity = Severity.WARN
    label = "Ignored ${block.name()}"
    info = "${block.title()}s and other statements are not processed inside swap scopes."
    hint = "Swap scope is defined at this location:\n${parent.ref.toUriString()}"
}

context(parent: RootScopeBuilder)
private fun reportRootCloser(closer: BlockToken.Code): Unit = closer.ref.report(LAYOUT_GROUP + "unmatched-root-closer") {
    label = "Unmatched ${closer.name()} closer"
    info = "${closer.title()} must have a corresponding `${closer.marker.text} ... {` comment."

    val candidate: BlockToken.Code? = parent.findScopeCandidate(closer)
    if (candidate != null) hint = "${candidate.title()} at this location may need to end with `{`:\n${candidate.ref.toUriString()}"
}

private fun RootScopeBuilder.findScopeCandidate(closer: BlockToken.Code): BlockToken.Code? {
    for (builder in entries.asReversed()) if (builder is CodeScopeBuilder)
        builder.findScopeCandidate(closer)?.let { return it }
    return null
}

private fun CodeScopeBuilder.findScopeCandidate(closer: BlockToken.Code): BlockToken.Code? {
    if (token === closer || token.marker neqtype closer.marker) return null
    child?.findScopeCandidate(closer)?.let { return it }
    for (builder in entries.asReversed()) if (builder is CodeScopeBuilder)
        builder.findScopeCandidate(closer)?.let { return it }
    return token.takeIf { it.definition.kind.isLine }
}

private fun reportMissingClosingBracket(code: BlockToken.Code): Unit = code.ref.report(LAYOUT_GROUP + "missing-scope-closer") {
    label = "Missing scope closer"
    info = "${code.title()} doesn't have a matching `${code.marker.text}}` closer."
    hint = "Add a `${code.marker.text}}` comment to close the scope opened here."
}

private fun reportInvalidCloserType(opener: BlockToken.Code, closer: BlockToken.Code): Unit = closer.ref.report(LAYOUT_GROUP + "mismatched-closer-type") {
    severity = Severity.WARN
    label = "Mismatched closer type"
    info = "${opener.title()} can't be closed with a ${closer.name()} marker."
    hint = buildString {
        appendLine("${opener.title()} at this location needs to be closed with `${opener.marker.text}}`:")
        append(opener.ref.toUriString())
    }
}

private val CodeScopeBuilder.ref: DocumentReference
    inline get() = token.ref

private fun BlockToken.title(): String =
    name().titlecase()

private fun BlockToken.name(): String = when (this) {
    is BlockToken.Content -> "text"
    is BlockToken.Comment -> "comment"
    is BlockToken.Root -> "root"
    is BlockToken.Code -> when (definition) {
        is DefinitionToken.Swap -> "swap"
        is DefinitionToken.Condition -> "condition"
        is DefinitionToken.Replacement -> "replacement"
    }
}

private fun FileAstBuilder.isNotBlank(): Boolean = when (this) {
    is ContentBuilder -> text.isNotBlank()
    is CommentBuilder -> text.isNotBlank()
    else -> true
}

private inline fun CodeScopeBuilder.anyAncestor(action: (CodeScopeBuilder) -> Boolean): Boolean {
    forEachAncestor { if (action(it)) return true }
    return false
}

private inline fun CodeScopeBuilder.forEachAncestor(action: (CodeScopeBuilder) -> Unit) {
    var next: ScopeBuilder = parent
    while (next is CodeScopeBuilder) {
        action(next)
        next = next.parent
    }
}

private fun BlockToken.Content.findLineSplit(immediate: Boolean): Int =
    leaf.text.findLineSplit(immediate)

private fun BlockToken.Comment.findLineSplit(immediate: Boolean): Int {
    val split = body.text.findLineSplit(immediate)
    return if (split >= 0 || body.text.isBlank() || '\n' !in opener?.text.orEmpty()) split else body.text.length
}

private fun BlockToken.Content.findLookupSplit(match: String?, capturing: Boolean): Int = when (match) {
    null -> leaf.text.findWordSplit()
    else -> leaf.text.findLookupSplit(match, capturing)
}

private fun BlockToken.Comment.findLookupSplit(match: String?, capturing: Boolean): Int = when (match) {
    null -> body.text.findWordSplit()
    else -> body.text.findLookupSplit(match, capturing)
}
