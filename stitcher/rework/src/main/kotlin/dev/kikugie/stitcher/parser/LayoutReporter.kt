@file:Suppress("NOTHING_TO_INLINE")

package dev.kikugie.stitcher.parser

import dev.kikugie.stitcher.antlr.StitcherParser.Tokens
import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.document.issue.LAYOUT_GROUP
import dev.kikugie.stitcher.document.issue.ProblemReport.Severity
import dev.kikugie.stitcher.document.issue.report
import dev.kikugie.stitcher.token.BlockToken
import dev.kikugie.stitcher.token.ScopeToken
import dev.kikugie.stitcher.token.typeValue
import dev.kikugie.stitcher.token.unquote
import dev.kikugie.stitcher.util.titlecase

internal fun RootBuilder.reportProblems(): Unit = accept(LayoutReporter, null)

private object LayoutReporter : BlockTokenBuilder.Visitor<Unit, SwapBuilder?> {
    // Leaf tokens carry no layout issues.
    override fun visitContent(content: ContentBuilder, ctx: SwapBuilder?) = Unit
    override fun visitComment(comment: CommentBuilder, ctx: SwapBuilder?) = Unit

    override fun visitRoot(root: RootBuilder, ctx: SwapBuilder?) =
        root.entries.forEach { it.accept(this, null) }

    override fun visitCode(code: CodeBuilder, ctx: SwapBuilder?) {
        // A definition directly inside a swap scope is an unparsed leaf - report it and don't descend.
        if (ctx != null) return reportSwapNesting(ctx, code)

        if (code.unmatchedCloser) reportRootCloser(code)
        if (code.kind.isScoped && !code.terminated && code.mismatchedCloser == null) reportMissingClosingBracket(code)
        code.mismatchedCloser?.let { reportInvalidCloserType(code, it) }
        code.reportUnsatisfiedLookup()

        val swap: SwapBuilder? = code as? SwapBuilder
        code.entries.forEach { it.accept(this, swap) }
    }
}

private fun CodeBuilder.reportUnsatisfiedLookup() {
    if (satisfiedLookup) return
    // Word lookups (no literal) always match the next word, so only a specific string can go unfound.
    val opener = definition.opener
    if (opener is ScopeToken.Lookup && opener.lookup != null)
        reportLookupStringNotFound(this, opener, lookupInterrupter)
}

// Issued when a code builder starting with } is not terminating any scope
private fun reportRootCloser(code: CodeBuilder): Unit = code.ref.report(LAYOUT_GROUP + "unmatched-root-closer") {
    label = "Unmatched ${code.name()} closer"
    info = "${code.title()} must have a corresponding `${code.marker.text} ... {` comment."

    // A preceding open-scope definition of the same marker was likely meant to be closed by this token.
    val candidate: CodeBuilder? = code.rootCloserCandidate()
    if (candidate != null) hint = buildString {
        appendLine("${candidate.title()} at this location may need to end with `{`:")
        append(candidate.ref.toUriString())
    }
}

// Locates an open-scope definition of the same marker - anywhere in the tree - that could have been closed instead
private fun CodeBuilder.rootCloserCandidate(): CodeBuilder? =
    (parent as? RootBuilder)?.findScopeCandidate(this)

// Depth-first search for the last (in document order) open scope this extra closer could have been meant to close
private fun ScopeBuilder.findScopeCandidate(closer: CodeBuilder): CodeBuilder? {
    var candidate: CodeBuilder? = null
    for (entry in entries) if (entry is CodeBuilder) {
        if (entry !== closer && entry.marker.type == closer.marker.type && entry.kind.isOpen) candidate = entry
        entry.findScopeCandidate(closer)?.let { candidate = it }
    }
    return candidate
}

// Issued when a scope is opened with { but never closed
private fun reportMissingClosingBracket(code: CodeBuilder): Unit = code.ref.report(LAYOUT_GROUP + "missing-scope-closer") {
    label = "Missing scope closer"
    info = "${code.title()} doesn't have a matching `${code.marker.text}}` closer."
    hint = "Add a `${code.marker.text}}` comment to close the scope opened here."
}

// Issued for every definition ignored inside a swap builder's scope
private fun reportSwapNesting(swap: CodeBuilder, code: CodeBuilder): Unit = code.ref.report(LAYOUT_GROUP + "definition-in-swap-scope") {
    severity = Severity.WARN
    label = "Ignored ${code.name()}"
    info = "${code.title()}s and other statements are not processed inside swap scopes."

    hint = buildString {
        appendLine("Swap scope is defined at this location:")
        append(swap.ref.toUriString())
    }
}

// Issued when a closer of the wrong marker ends a typed scope; i.e.
// `? if true { ... ~}` - ERR
// `? if true ... ~}` - OK because it closes the replacement before
// `$ id { ... ?}` - OK because it's reported as swap nesting instead
private fun reportInvalidCloserType(opener: CodeBuilder, closer: CodeBuilder): Unit = closer.ref.report(LAYOUT_GROUP + "mismatched-closer-type") {
    severity = Severity.WARN
    label = "Mismatched closer type"
    info = "${opener.title()} can't be closed with a ${closer.name()} marker."

    hint = buildString {
        appendLine("${opener.title()} at this location needs to be closed with `${opener.marker.text}}`:")
        append(opener.ref.toUriString())
    }
}

// Issued when a lookup scope builder with a specific match (i.e. token.lookup != null) is not found.
// The [interrupter] that ended the search may be a nested definition or a closer of any enclosing scope.
private fun reportLookupStringNotFound(code: CodeBuilder, token: ScopeToken.Lookup, interrupter: BlockToken.Code?): Unit = code.ref.report(LAYOUT_GROUP + "lookup-not-found") {
    label = "Lookup string not found"
    info = "${code.title()} expected to find '${token.lookup?.unquote()}' in the text ahead."

    if (interrupter != null) hint = buildString {
        appendLine("${interrupter.title()} at this location stops the search:")
        append(interrupter.ref.toUriString())
    }
}

private val BlockTokenBuilder.ref: DocumentReference
    inline get() = stub.ref

private fun BlockTokenBuilder.title(): String =
    name().titlecase()

private fun BlockTokenBuilder.name(): String = when (this) {
    is ContentBuilder -> "text"
    is CommentBuilder -> "comment"
    is ConditionBuilder -> "condition"
    is SwapBuilder -> "swap"
    is ReplacementBuilder -> "replacement"
    is CodeBuilder -> "definition"
    is RootBuilder -> "root"
}

// A definition token isn't yet wrapped in a builder when it merely interrupts a lookup from an enclosing scope.
private fun BlockToken.Code.title(): String =
    name().titlecase()

private fun BlockToken.Code.name(): String = when (marker.typeValue) {
    Tokens.COND_MARK -> "condition"
    Tokens.SWAP_MARK -> "swap"
    Tokens.REPL_MARK -> "replacement"
    else -> "definition"
}
