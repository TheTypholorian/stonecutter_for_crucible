package dev.kikugie.stitcher.parser

import dev.kikugie.stitcher.antlr.*
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.bail
import dev.kikugie.stitcher.issue.locate
import dev.kikugie.stitcher.issue.report
import dev.kikugie.stitcher.parser.layout.MarkedTokenBuilder
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.DefinitionToken.Kind
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.token.impl.ScopeToken
import dev.kikugie.stitcher.util.LINE_BREAKS
import dev.kikugie.stitcher.util.WHITESPACES
import dev.kikugie.stitcher.util.errorListener
import dev.kikugie.stitcher.util.unquote
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.TokenStream
import java.util.*

private val MARKERS = charArrayOf('?', '$', '~')

internal class FileAstParser private constructor(
    val stream: TokenStream,
    val source: CharStream,
    val factory: StitcherTokenFactory,
    val problems: FileProblemReporter
) {
    companion object {
        fun parse(stream: TokenStream, source: CharStream, factory: StitcherTokenFactory, problems: FileProblemReporter): BlockToken.Root =
            FileAstParser(stream, source, factory, problems).build()
    }

    private val stack: Deque<BlockBuilder.Scoped> = ArrayDeque(4)
    private val builder: MarkedTokenBuilder = MarkedTokenBuilder(factory, problems)
    private val listener: StitcherErrorListener = StitcherErrorListener(problems)

    init {
        stack += RootBuilder()
    }

    private fun build(): BlockToken.Root {
        while (stream.LA(1) != AntlrToken.EOF) when (NativeStitcherType.of(stream.LA(1))) {
            NativeStitcherType.CONTENT -> consumeContentBlock(stream.advance())
            NativeStitcherType.COMMENT_OPEN -> consumeCommentBlock(stream.advance(), stream.advance(), stream.advance())
            else -> problems.reportUnknownToken(stream.advance())
        }

        while (stack.isNotEmpty()) when (val it = stack.removeLast()) {
            is CodeBuilder -> it.finalize(problems, true)
            is RootBuilder -> return it.build(factory)
        }

        problems.reportConsumedRoot()
    }

    private tailrec fun consumeBlockBuilder(block: BlockBuilder): Unit = when (val it = stack.peekLast().accept(block)) {
        // Can accept more – so do nothing
        BlockAcceptResult.ConsumedOpen -> Unit

        // Can't accept more – so pop the scope
        BlockAcceptResult.ConsumedFinal ->
            stack.removeLast().finalize(problems, false)

        // Can't accept this – pop the scope and try again
        BlockAcceptResult.Rejected -> {
            stack.removeLast().finalize(problems, false)
            consumeBlockBuilder(block)
        }

        // Can't accept more – pop the scope and accept the remainder
        is BlockAcceptResult.ConsumedPartial -> {
            stack.removeLast().finalize(problems, false)
            consumeBlockBuilder(it.remainder)
        }
    }

    private fun consumeContentBlock(token: AntlrToken) {
        if (token.text.isNotEmpty()) consumeBlockBuilder(ContentBuilder(token))
    }

    private fun consumeCommentBlock(opener: AntlrToken, body: AntlrToken, closer: AntlrToken) {
        val comment = CommentBuilder(opener, body, closer)
        val (marker, definition) = parseCommentBody(body)
            ?: return consumeBlockBuilder(comment)

        val code = CodeBuilder(comment, marker, definition)
        if (code.kind == Kind.INDEPENDENT) consumeBlockBuilder(code)
        else consumeCodeBlock(code)
    }

    private fun consumeCodeBlock(code: CodeBuilder): Unit = when (val it = stack.peekLast()) {
        is CodeBuilder -> consumeNestedCodeBlock(code, it)
        is RootBuilder -> consumeRootCodeBlock(code)
    }

    private fun consumeRootCodeBlock(code: CodeBuilder) {
        consumeBlockBuilder(code)

        // `}` is always invalid in the root scope
        if (code.def.closer != null) problems.reportRootCloser(code, stack.peekLast())

        if (!code.kind.isEmpty) stack.addLast(code)
    }

    private fun consumeNestedCodeBlock(code: CodeBuilder, parent: CodeBuilder) {
        if (code.kind.isExtension) {
            // Scopes should be closed by a comment with the same type
            if (code.mark.type != parent.mark.type) problems.reportMismatchedCloser(code, parent)
            // Occurs if the previous scope is incomplete
            else if (parent.kind.isOpen) problems.reportUnmatchedExtension(code, parent)
            stack.removeLast().finalize(problems, false)
        }

        consumeBlockBuilder(code)
        if (!code.kind.isEmpty) stack.addLast(code)
    }

    private fun parseCommentBody(body: AntlrToken): MarkedTokenBuilder.Result? = InlineCharStream(body).use { input ->
        if (input.LC(1) !in MARKERS) return@use null

        val lexer = StitcherLexer(input).apply {
            errorListener = listener
            tokenFactory = InlineTokenFactory(body.lineIndex, body.charPositionInLine)
        }
        val parser = StitcherParser(CommonTokenStream(lexer)).apply {
            errorListener = listener
            errorHandler = StitcherErrorStrategy
        }
        parser.runCatching { definition().accept(builder) }
            .onFailure { problems.reportParserError(body, it) }
            .getOrNull()
    }
}

private sealed interface BlockAcceptResult {
    object Rejected : BlockAcceptResult
    object ConsumedOpen : BlockAcceptResult
    object ConsumedFinal : BlockAcceptResult
    class ConsumedPartial(val remainder: BlockBuilder) : BlockAcceptResult
}

private sealed interface BlockBuilder {
    fun build(factory: StitcherTokenFactory): BlockToken

    sealed interface Scoped : BlockBuilder {
        val entries: List<BlockBuilder>

        fun accept(block: BlockBuilder): BlockAcceptResult
        fun finalize(problems: FileProblemReporter, done: Boolean) {}
    }
}

private class ContentBuilder(val start: Int, val end: Int, val text: String) : BlockBuilder {
    constructor(token: AntlrToken) : this(token.startIndex, token.endIndex, token.text)

    override fun build(factory: StitcherTokenFactory): BlockToken.Content = BlockToken.Content(
        factory.create(NativeStitcherType.CONTENT, start, end, text)
    )
}

private class CommentBuilder(val opener: AntlrToken?, val body: AntlrToken, val closer: AntlrToken?) : BlockBuilder {
    val text: String by lazy { body.text }

    override fun build(factory: StitcherTokenFactory): BlockToken.Comment = BlockToken.Comment(
        factory.fromAntlrToken(opener), factory.fromAntlrToken(body), factory.fromAntlrToken(closer)
    )
}

private class RootBuilder(override val entries: MutableList<BlockBuilder> = mutableListOf()) : BlockBuilder.Scoped {
    override fun build(factory: StitcherTokenFactory): BlockToken.Root = BlockToken.Root(
        entries.map { it.build(factory) }
    )

    override fun accept(block: BlockBuilder): BlockAcceptResult {
        entries += block
        return BlockAcceptResult.ConsumedOpen
    }
}

private class CodeBuilder(
    val host: CommentBuilder, val mark: AntlrToken, val def: DefinitionToken,
    override val entries: MutableList<BlockBuilder> = mutableListOf()
) : BlockBuilder.Scoped {
    val kind: Kind
        inline get() = def.kind
    var satisfied: Boolean = kind.isScoped
        private set

    override fun build(factory: StitcherTokenFactory): BlockToken.Code = BlockToken.Code(
        host.build(factory), factory.fromAntlrToken(mark), def, entries.map { it.build(factory) }
    )

    override fun finalize(problems: FileProblemReporter, done: Boolean): Unit = when (val opener = def.opener) {
        is ScopeToken.Lookup if opener.lookup != null && !satisfied -> problems.reportLookupNotFound(opener)
        is ScopeToken.Closed if done -> problems.reportUnclosedScope(opener, mark)
        else -> null
    }.let { }

    override fun accept(block: BlockBuilder): BlockAcceptResult = when (kind) {
        Kind.LOOKUP_OPENER, Kind.LOOKUP_EXTENSION -> if (satisfied) BlockAcceptResult.Rejected else acceptLookup(block)
        Kind.LINE_OPENER, Kind.LINE_EXTENSION -> if (satisfied) BlockAcceptResult.Rejected else acceptLine(block)
        Kind.SCOPED_OPENER, Kind.SCOPED_EXTENSION -> {
            entries += block; BlockAcceptResult.ConsumedOpen
        }

        else -> BlockAcceptResult.Rejected
    }

    private fun acceptLine(block: BlockBuilder): BlockAcceptResult = when (block) {
        is ContentBuilder -> {
            val split = block.text.findLineSplit(isImmediate())
            consumeContentSplit(block, split)
        }

        is CommentBuilder -> {
            var split = block.text.findLineSplit(isImmediate())
            if (split < 0 && (block.closer?.text?.lastOrNull() ?: ' ') in LINE_BREAKS)
                split = block.text.length
            consumeCommentSplit(block, split)
        }

        else -> BlockAcceptResult.Rejected
    }

    private fun acceptLookup(block: BlockBuilder): BlockAcceptResult {
        if (block is BlockBuilder.Scoped) return BlockAcceptResult.Rejected

        val opener = def.opener as? ScopeToken.Lookup
            ?: return BlockAcceptResult.Rejected
        return acceptLookup(block, opener)
    }

    private fun acceptLookup(block: BlockBuilder, scope: ScopeToken.Lookup): BlockAcceptResult = when (block) {
        is ContentBuilder -> {
            val match = scope.lookup?.unquote()
            val split = if (match == null) block.text.findWordSplit()
            else block.text.findLookupSplit(match, scope.plus != null)

            consumeContentSplit(block, split)
        }

        is CommentBuilder -> {
            val match = scope.lookup?.unquote()
            val split = if (match == null) block.text.findWordSplit()
            else block.text.findLookupSplit(match, scope.plus != null)

            consumeCommentSplit(block, split)
        }

        else -> BlockAcceptResult.Rejected
    }

    private fun consumeContentSplit(block: ContentBuilder, split: Int): BlockAcceptResult = when (split) {
        -1 -> {
            entries += block; BlockAcceptResult.ConsumedOpen
        }

        0 -> {
            satisfied = true; BlockAcceptResult.Rejected
        }

        block.text.length -> consumeFinal(block)

        else -> with(block) {
            satisfied = true
            entries += ContentBuilder(start, start + split, text.take(split))
            ContentBuilder(start + split, end, text.substring(split)).let(BlockAcceptResult::ConsumedPartial)
        }
    }

    private fun consumeCommentSplit(block: CommentBuilder, split: Int): BlockAcceptResult = when (split) {
        -1 -> {
            entries += block; BlockAcceptResult.ConsumedOpen
        }

        0 -> {
            satisfied = true; BlockAcceptResult.Rejected
        }

        block.text.length -> consumeFinal(block)

        else -> with(block) {
            satisfied = true

            val partial = CommonToken(body).apply { text = text.take(split); endIndex = startIndex + split }
            entries += CommentBuilder(opener, partial, null)

            val remainder = CommonToken(body).apply { text = text.substring(split); startIndex += split }
            CommentBuilder(null, remainder, closer).let(BlockAcceptResult::ConsumedPartial)
        }
    }

    private fun consumeFinal(block: BlockBuilder): BlockAcceptResult {
        satisfied = true
        entries += block
        return BlockAcceptResult.ConsumedFinal
    }

    private fun isImmediate(): Boolean = entries.any(BlockBuilder::isNotBlank)
}

private fun TokenStream.advance(): AntlrToken =
    LT(1).also { consume() }

private fun FileProblemReporter.reportConsumedRoot(): Nothing = bail(
    LAYOUT_GROUP.child("consumed-root"), 0, 0
) {
    label = "The root block builder has been eaten!"
    details = """
        Parser couldn't reach a return condition, causing a critical failure.
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues.
    """.trimIndent()
}

private fun FileProblemReporter.reportUnknownToken(token: AntlrToken): Nothing = bail(
    LAYOUT_GROUP.child("unexpected-token"), token
) {
    label = "Unknown layout token"
    details = """
        Token type ${token.type} is not expected to be produced by the token stream.
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues.
    """.trimIndent()
}

private fun FileProblemReporter.reportRootCloser(code: CodeBuilder, parent: BlockBuilder.Scoped): Any = report(
    LAYOUT_GROUP.child("unmatched-root-closer"), code.host.body
) {
    label = "Unmatched scope closer"
    details = "Definition must have a corresponding `${code.mark.text} ... {` opener."

    val candidate = parent.entries.lastOrNull {
        it !== code && it is CodeBuilder && it.mark.type == code.mark.type && it.host.opener == null
    }

    if (candidate is CodeBuilder) hint = buildString {
        appendLine(locate(candidate.host.body.endIndex).toLinkString())
        append("may need to end with `{`.")
    }
}

private fun FileProblemReporter.reportMismatchedCloser(code: CodeBuilder, parent: CodeBuilder): Any = report(
    LAYOUT_GROUP.child("mismatched-closer-type"), code.host.body
) {
    label = "Mismatched closer type"
    details = "Expected `${parent.mark.text}}` instead of `${code.mark.text}}`."
    hint = buildString {
        appendLine(locate(parent.host.body.startIndex).toLinkString())
        append("may need to be closed with `${parent.mark.text}}`.")
    }
}

private fun FileProblemReporter.reportUnmatchedExtension(code: CodeBuilder, parent: CodeBuilder): Any = report(
    LAYOUT_GROUP.child("unmatched-extension"), code.host.body
) {
    label = "Mismatched extension"
    details = "The definition closes a scope that doesn't end with a `{`."
    hint = buildString {
        appendLine(locate(parent.host.body.endIndex).toLinkString())
        append("may need to end with `{`.")
    }
}

private fun FileProblemReporter.reportParserError(body: AntlrToken, err: Throwable): Any = report(
    LAYOUT_GROUP.child("parse-failure"), body
) {
    label = "Invalid expression"
    details = """
        Parser tried to do its job and had a burnout ¯\_(ツ)_/¯.
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues.
    """.trimIndent()
    exception = err
}

private fun FileProblemReporter.reportUnclosedScope(opener: StitcherToken, marker: AntlrToken): Any = report(
    LAYOUT_GROUP.child("unclosed-scope"), opener
) {
    label = "Unclosed scope"
    details = "Missing closing `${marker.text}}` comment."
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#closed"
}

private fun FileProblemReporter.reportLookupNotFound(opener: ScopeToken.Lookup): Any = report(
    LAYOUT_GROUP.child("lookup-not-found"), opener
) {
    label = "Lookup string not found"
    details = "The pattern '${opener.lookup?.unquote()}' wasn't found in the text ahead."
    hint = "The pattern search is interrupted by any condition, swap or replacement comments."
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#lookup"
}

private tailrec fun String.findLineSplit(immediate: Boolean, start: Int = 0): Int = when (val index = indexOfAny(LINE_BREAKS, start)) {
    -1 -> -1
    else if immediate -> index
    else if (start until index).any { get(it) !in WHITESPACES } -> index
    else -> findLineSplit(false, index + 1)
}

private fun String.findWordSplit(): Int {
    var index = indexOfFirst { it !in WHITESPACES }
    if (index < 0) return -1

    index = indexOfAny(WHITESPACES, index)
    return if (index < 0) length else index
}

private fun String.findLookupSplit(match: String, capturing: Boolean): Int = when (val index = indexOf(match)) {
    -1 -> -1
    else if !capturing -> index
    else -> index + match.length
}

private fun BlockBuilder.isNotBlank(): Boolean = when (this) {
    is ContentBuilder -> text.isNotBlank()
    is CommentBuilder -> body.text.isNotBlank()
    is RootBuilder -> entries.all(BlockBuilder::isNotBlank)
    is CodeBuilder -> false
}
