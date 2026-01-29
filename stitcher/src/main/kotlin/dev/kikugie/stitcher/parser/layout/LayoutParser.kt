package dev.kikugie.stitcher.parser.layout

import dev.kikugie.stitcher.antlr.*
import dev.kikugie.stitcher.issue.*
import dev.kikugie.stitcher.parser.LAYOUT_GROUP
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.component.DefinitionBuilder
import dev.kikugie.stitcher.token.StitcherType.Layout
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.DefinitionToken.Kind
import dev.kikugie.stitcher.util.*
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token.EOF
import org.antlr.v4.runtime.TokenStream
import org.antlr.v4.runtime.misc.Pair
import java.util.*

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
    val problems: FileProblemReporter,
) {
    private val stack: Deque<BlockBuilder.Scoped> = ArrayDeque(4)
    private val visitor: StitcherVisitor<kotlin.Pair<AntlrToken, DefinitionToken>> = DefinitionBuilder.paired(factory, problems)

    init {
        stack += RootBuilder(factory)
    }

    private fun collect(): BlockToken.Root {
        consumeTokenStream()
        return finalizeUnfinishedScopes()
    }

    private fun consumeTokenStream() {
        while (stream.LA(1) != EOF) when (Layout.getOrNull(stream.LA(1))) {
            Layout.CONTENT -> handleContent(stream.advance())
            Layout.COMMENT_OPEN -> handleComment(stream.advance(), stream.advance(), stream.advance())
            else -> reportUnknownToken(stream.advance())
        }
    }

    private fun finalizeUnfinishedScopes(): BlockToken.Root {
        while (stack.isNotEmpty()) when (val it = stack.removeLast()) {
            is RootBuilder -> return it.build()
            is CodeBuilder -> it.finalize(problems, true)
        }

        problems.bail(LAYOUT_GROUP.child("consumed-root"), 0, 0) {
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

    private fun reportUnknownToken(token: AntlrToken): Nothing {
        problems.bail(LAYOUT_GROUP.child("unexpected-token"), token) {
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
        problems.report(LAYOUT_GROUP.child("unmatched-root-closer"), code.source.body) {
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
        problems.report(LAYOUT_GROUP.child("unmatched-root-closer"), code.source.body) {
            label = "Mismatched closer type"
            details = "Expected `${parent.marker.text}}` instead of `${code.marker.text}}`."
        }
        problems.report(LAYOUT_GROUP.child("mismatched-child-suggestion"), parent.source.body) {
            severity = ProblemSeverity.WARN
            label = "Missing scope closer"
            details = "The definition may need to be closed with `${parent.marker.text}}` to resolve a previously reported error."
        }
    }

    private fun reportUnmatchedExtension(code: CodeBuilder, parent: CodeBuilder) {
        problems.report(LAYOUT_GROUP.child("unmatched-extension"), code.source.body) {
            label = "Mismatched extension"
            details = "The definition closes a scope that doesn't end with a `{`."
        }
        suggestMissingOpener(parent)
    }

    private fun suggestMissingOpener(parent: CodeBuilder) {
        problems.report(LAYOUT_GROUP.child("unmatched-closer-candidate"), parent.source.body) {
            severity = ProblemSeverity.INFO
            label = "Potential missing opener"
            details = "The definition may need to end with a `{` to resolve a previously reported error."
        }
    }

    private fun parseCommentBody(body: AntlrToken): kotlin.Pair<AntlrToken, DefinitionToken>? {
        if (body.stopIndex < body.startIndex)
            return null // Empty comment body

        val input = body.text.charStream()
        when (input.LC(1)) {
            '?', '$', '~' -> {}
            else -> return null
        }

        val location = problems.location(body)
        val listener = InlineErrorListener(location, problems)
        val scanner = StitcherLexer(input).errorListener(listener)
        val stream = InlineTokenStream(Pair(scanner, source), CommonTokenStream(scanner), location)
        val parser = StitcherParser(stream).errorListener(listener)
        return parser.definition().accept(visitor)
    }

    companion object {
        fun parse(stream: TokenStream, source: CharStream, factory: StitcherTokenFactory, problems: FileProblemReporter): BlockToken.Root =
            LayoutParser(stream, source, factory, problems).collect()
    }
}

private fun TokenStream.advance(): AntlrToken =
    LT(1).also { consume() }
