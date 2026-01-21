package dev.kikugie.stitcher.parser.layout

import dev.kikugie.stitcher.antlr.*
import dev.kikugie.stitcher.issue.ProblemID
import dev.kikugie.stitcher.issue.ProblemLocation
import dev.kikugie.stitcher.issue.ProblemReporter
import dev.kikugie.stitcher.issue.ProblemSeverity
import dev.kikugie.stitcher.issue.ProblemSource
import dev.kikugie.stitcher.issue.at
import dev.kikugie.stitcher.issue.bail
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.issue.v2.*
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.component.DefinitionBuilder
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.DefinitionToken.Kind
import dev.kikugie.stitcher.util.*
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Token.EOF
import org.antlr.v4.runtime.TokenStream
import org.antlr.v4.runtime.Vocabulary
import org.antlr.v4.runtime.VocabularyImpl
import java.util.*

private val CONSUMED_ROOT: ProblemID = ProblemID.LAYOUT_GROUP.create("consumed-root")
private val UNEXPECTED_TOKEN: ProblemID = ProblemID.LAYOUT_GROUP.create("unexpected-token")
private val UNMATCHED_CLOSER: ProblemID = ProblemID.LAYOUT_GROUP.create("unmatched-root-closer")
private val CLOSER_CANDIDATE: ProblemID = ProblemID.LAYOUT_GROUP.create("unmatched-closer-candidate")
private val MISMATCHED_CLOSER: ProblemID = ProblemID.LAYOUT_GROUP.create("mismatched-child-closer")
private val MISMATCH_SUGGESTION: ProblemID = ProblemID.LAYOUT_GROUP.create("mismatched-child-suggestion")
private val UNMATCHED_EXTENSION: ProblemID = ProblemID.LAYOUT_GROUP.create("unmatched-extension")

/**
 * Parses a [stream] of comment tokens into an AST.
 *
 * The tree consists of two component types:
 * - Blocks: content, comments, and code comments;
 * - Scopes: collections of blocks.
 *
 * The parser works with a scope stack, pushing incoming blocks to it.
 * The scope may accept the block or reject it, which means it should be closed.
 */
internal class LayoutParser private constructor(
    val stream: TokenStream,
    val source: CharStream,
    val factory: StitcherTokenFactory,
    val problems: ProblemReporter,
) {
    private val stack: Deque<BlockBuilder.Scoped> = ArrayDeque(4)
    private val visitor: StitcherVisitor<Pair<AntlrToken, DefinitionToken>> = DefinitionBuilder.paired(factory, problems)

    init {
        stack += RootBuilder(factory)
    }

    private fun collect(): BlockToken.Root {
        consumeTokenStream()
        return finalizeUnfinishedScopes()
    }

    private fun consumeTokenStream() {
        while (stream.LA(1) != EOF) when (stream.LA(1)) {
            CONTENT -> handleContent(stream.advance())
            COMMENT_OPEN -> handleComment(stream.advance(), stream.advance(), stream.advance())
            else -> reportUnknownToken(stream.advance())
        }
    }

    private fun finalizeUnfinishedScopes(): BlockToken.Root {
        while (stack.isNotEmpty()) when (val it = stack.removeLast()) {
            is RootBuilder -> return it.build()
            is CodeBuilder -> it.finalize(problems, true)
        }

        problems.bail(CONSUMED_ROOT, ProblemLocation.UNDEFINED) {
            label = "The root block builder has been eaten!"
            details = "The parser has not reached a return condition, causing a critical failure." +
                "This shouldn't ever happen, so please report this to https://codeberg.org/stonecutter/stonecutter/issues."
            solution = "Panic."
        }
    }

    private fun acceptBlock(block: BlockBuilder): Unit = when (val result = stack.peekLast().accept(block)) {
        // Can accept more - do nothing
        BlockAcceptResult.ConsumedOpen -> Unit

        // Pop the current scope
        BlockAcceptResult.ConsumedFinal ->
            stack.removeLast().finalize(problems, false)

        // Pop the current scope and push to the next
        BlockAcceptResult.Rejected -> {
            stack.removeLast().finalize(problems, false)
            acceptBlock(block)
        }

        // Pop the current scope and push remainder to the next
        is BlockAcceptResult.ConsumedPartial -> {
            stack.removeLast().finalize(problems, false)
            acceptBlock(result.remainder)
        }
    }

    private fun reportUnknownToken(token: AntlrToken) {
        problems.bail(UNEXPECTED_TOKEN, token) {
            label = "Unknown layout token"
            details = "Token type ${token.type} is not expected to be produced by the token stream. " +
                "This shouldn't ever happen, so please report this to https://codeberg.org/stonecutter/stonecutter/issues."
            solution = "Panic."
        }
    }

    private fun handleContent(token: AntlrToken) {
        if (token.text.isNotEmpty()) acceptBlock(ContentBuilder(factory, token))
    }

    private fun handleComment(opener: AntlrToken, body: AntlrToken, closer: AntlrToken) {
        val comment = CommentBuilder(factory, opener, body, closer)
        val (marker, definition) = parseCommentBody(body)
            ?: return acceptBlock(comment)

        val code = CodeBuilder(factory, comment, marker, definition)
        if (code.kind == Kind.INDEPENDENT) acceptBlock(code)
        else handleCode(code)
    }

    private fun handleCode(code: CodeBuilder): Unit = when (val parent = stack.peekLast()) {
        is RootBuilder -> handleRootCode(code)
        is CodeBuilder -> handleNestedCode(code, parent)
    }

    private fun LayoutParser.handleRootCode(code: CodeBuilder) {
        acceptBlock(code)
        // '}' shouldn't be possible in the root scope
        if (code.definition.closer != null)
            reportUnmatchedCloser(code)

        // Add invalid extensions to the scope stack anyway
        if (code.kind != Kind.CLOSER)
            stack.addLast(code)
    }

    private fun LayoutParser.handleNestedCode(code: CodeBuilder, parent: CodeBuilder) {
        if (code.kind.isExtension) {
            // Check for situations like `? if condition { ... $}`, in which case we close it anyway
            if (parent.marker.type != code.marker.type)
                reportMismatchedCloser(code, parent)
            // Occurs if we have an unfinished open scope, in which case we close it prematurely
            else if (parent.kind.isOpen)
                reportUnmatchedExtension(code, parent)
            stack.removeLast().finalize(problems, false)
        }

        acceptBlock(code)
        if (!code.kind.isEmpty)
            stack.addLast(code)
    }

    private fun reportUnmatchedCloser(code: CodeBuilder) {
        problems.report(UNMATCHED_CLOSER, code.source.body) {
            label = "Unmatched scope closer"
            details = "The definition must have a corresponding `${code.marker.text} ... {` opener."
        }

        val candidate = stack.peekFirst().entries.lastOrNull {
            it is CodeBuilder && it.marker.type == code.marker.type && it.definition.opener == null
        }
        if (candidate is CodeBuilder)
            suggestMissingOpener(candidate)
    }

    private fun reportMismatchedCloser(code: CodeBuilder, parent: CodeBuilder) {
        problems.report(MISMATCHED_CLOSER, code.source.body) {
            label = "Mismatched closer type"
            details = "Expected `${parent.marker.text}}` instead of `${code.marker.text}}`."
        }
        problems.report(MISMATCH_SUGGESTION, parent.source.body) {
            severity = ProblemSeverity.WARN
            label = "Missing scope closer"
            details = "The definition may need to be closed with `${parent.marker.text}}` to resolve a previously reported error."
        }
    }

    private fun reportUnmatchedExtension(code: CodeBuilder, parent: CodeBuilder) {
        problems.report(UNMATCHED_EXTENSION, code.source.body) {
            label = "Mismatched extension"
            details = "The definition closes a scope that doesn't end with a `{`."
        }
        suggestMissingOpener(parent)
    }

    private fun suggestMissingOpener(parent: CodeBuilder) {
        problems.report(CLOSER_CANDIDATE, parent.source.body) {
            severity = ProblemSeverity.INFO
            label = "Potential missing opener"
            details = "The definition may need to end with a `{` to resolve a previously reported error."
        }
    }

    private fun parseCommentBody(body: AntlrToken): Pair<AntlrToken, DefinitionToken>? {
        if (body.stopIndex < body.startIndex)
            return null // Empty comment body

        val input = body.text.toStream()
        when (input.LC(1)) {
            '?', '$', '~' -> {}
            else -> return null
        }

        val listener = InlineErrorListener(at(body.startIndex), problems)
        val scanner = StitcherLexer(input).errorListener(listener)
        val stream = InlineTokenStream(scanner, source, body.startIndex, at(body.startIndex))
        val parser = StitcherParser(stream).errorListener(listener)
        return parser.definition().accept(visitor)
    }

    companion object {
        fun parse(stream: TokenStream, source: CharStream, factory: StitcherTokenFactory, problems: ProblemReporter): BlockToken.Root =
            LayoutParser(stream, source, factory, problems).collect()
    }
}

private fun TokenStream.advance(): AntlrToken =
    LT(1).also { consume() }

private fun Int.scopeType(): String = when (this) {
    StitcherLexer.COND_MARK -> "condition"
    StitcherLexer.SWAP_MARK -> "swap"
    StitcherLexer.REPL_MARK -> "replacement"
    else -> "unknown"
}