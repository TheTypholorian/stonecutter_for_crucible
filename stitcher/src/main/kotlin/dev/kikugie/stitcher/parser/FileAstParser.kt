package dev.kikugie.stitcher.parser

import dev.kikugie.commons.takeAsOrNull
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
import dev.kikugie.stitcher.token.impl.ScopeToken
import dev.kikugie.stitcher.util.*
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.TokenStream
import java.util.*

private val MARKERS: CharArray = charArrayOf('?', '$', '~')

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

    private val stack: BlockBuilderStack = BlockBuilderStack(problems)
    private val builder: MarkedTokenBuilder = MarkedTokenBuilder(factory, problems)
    private val listener: StitcherErrorListener = StitcherErrorListener(problems)

    private fun build(): BlockToken.Root {
        while (stream.LA(1) != AntlrToken.EOF) when (NativeStitcherType.of(stream.LA(1))) {
            NativeStitcherType.CONTENT -> processContent(nextToken())
            NativeStitcherType.COMMENT_OPEN -> processComment(nextToken(), nextToken(), nextToken())
            else -> problems.bailUnknownToken(nextToken())
        }
        return stack.build(factory)
    }

    private fun processContent(token: AntlrToken) {
        if (token.text.isNotEmpty()) stack.handle(ContentBuilder(token))
    }

    private fun processComment(opener: AntlrToken, body: AntlrToken, closer: AntlrToken) {
        val comment = CommentBuilder(opener, body, closer)
        val (marker, definition) = parseCommentBody(body)
            ?: return stack.handle(comment)
        stack.handle(CodeBuilder(comment, marker, definition))
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

    private fun nextToken(): AntlrToken =
        stream.LT(1).also { stream.consume() }
}

private class BlockBuilderStack(val problems: FileProblemReporter){
    private val direct: Deque<BlockBuilder.Scoped> = ArrayDeque(4)

    val top: BlockBuilder.Scoped
        get() = direct.peekLast()

    init {
        direct += RootBuilder()
    }

    fun build(factory: StitcherTokenFactory): BlockToken.Root {
        while (direct.isNotEmpty()) when (val it = pop(true)) {
            is RootBuilder -> return it.build(factory)
            else -> {}
        }

        problems.bailConsumedRoot()
    }

    fun pop(done: Boolean = false): BlockBuilder.Scoped =
        direct.removeLast().apply { finalize(problems, done) }

    fun push(block: BlockBuilder.Scoped): Unit =
        direct.addLast(block)

    fun replace(block: CodeBuilder) {
        pop()
        top.entries += block
        if (!block.kind.isEmpty) push(block)
    }

    tailrec fun handle(builder: BlockBuilder): Unit = when (val it = top.accept(builder, this)) {
        // Can accept more – do nothing
        BlockAcceptResult.ConsumedOpen -> Unit

        // Can't accept more – pop the scope
        BlockAcceptResult.ConsumedFinal -> {
            pop().let { }
        }

        // Can't accept this – pop the scope and try again
        BlockAcceptResult.Rejected -> {
            pop()
            handle(builder)
        }

        // Can't accept more – pop the scope and accept the remainder
        is BlockAcceptResult.ConsumedPartial -> {
            pop()
            handle(it.remainder)
        }
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
        val entries: MutableList<BlockBuilder>

        fun accept(block: BlockBuilder, stack: BlockBuilderStack): BlockAcceptResult
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
        factory.fromAntlrTokenOrNull(opener), factory.fromAntlrToken(body), factory.fromAntlrTokenOrNull(closer)
    )
}

private class RootBuilder(override val entries: MutableList<BlockBuilder> = mutableListOf()) : BlockBuilder.Scoped {
    override fun build(factory: StitcherTokenFactory): BlockToken.Root = BlockToken.Root(
        entries.map { it.build(factory) }
    )

    override fun accept(block: BlockBuilder, stack: BlockBuilderStack): BlockAcceptResult = when (block) {
        is CodeBuilder -> acceptCode(block, stack)
        else -> {
            entries += block
            BlockAcceptResult.ConsumedOpen
        }
    }

    private fun acceptCode(code: CodeBuilder, stack: BlockBuilderStack): BlockAcceptResult {
        // `}` is always invalid in the root scope
        if (code.def.closer != null) stack.problems.reportRootCloser(code, this)

        // For line extensions, try merging empty content into the previous scope
        else if (code.def is DefinitionToken.Condition && code.kind.isExtension) with(stack.top) {
            val candidate = findLineConditionCandidate()
            if (candidate != null) mergeDanglingBlocks(candidate)
        }

        entries += code
        stack.push(code)
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
        is ScopeToken.Closed if done -> problems.reportUnclosedScope(this, opener)
        else -> null
    }.let { }

    override fun accept(block: BlockBuilder, stack: BlockBuilderStack): BlockAcceptResult = when (kind) {
        Kind.LINE_OPENER, Kind.LINE_EXTENSION -> acceptLine(block, stack)
        Kind.SCOPED_OPENER, Kind.SCOPED_EXTENSION -> acceptClosed(block, stack)
        Kind.LOOKUP_OPENER, Kind.LOOKUP_EXTENSION -> acceptLookup(block, stack)
        else -> BlockAcceptResult.Rejected
    }

    private fun acceptClosed(block: BlockBuilder, stack: BlockBuilderStack): BlockAcceptResult = when (block) {
        is CodeBuilder if (block.kind.isCloser) -> {
            if (block.mark.type != mark.type) stack.problems.reportMismatchedCloser(block, this)
            stack.replace(block)
            BlockAcceptResult.ConsumedOpen
        }

        is CodeBuilder -> {
            entries += block
            stack.push(block)
            BlockAcceptResult.ConsumedOpen
        }

        else -> {
            entries += block
            BlockAcceptResult.ConsumedOpen
        }
    }

    private fun acceptLine(block: BlockBuilder, stack: BlockBuilderStack): BlockAcceptResult = when (block) {
        is ContentBuilder if (!satisfied) -> {
            val split = block.text.findLineSplit(isImmediate())
            consumeContentSplit(block, split)
        }

        is CommentBuilder if (!satisfied) -> {
            var split = block.text.findLineSplit(isImmediate())
            if (split < 0 && (block.closer?.text?.lastOrNull() ?: ' ') in LINE_BREAKS)
                split = block.text.length
            consumeCommentSplit(block, split)
        }

        is CodeBuilder if (block.kind.isExtension) -> {
            if (block.def.closer == null && def.opener is ScopeToken.Closed)
                stack.problems.reportUnmatchedImplicitExtension(block, this)
            stack.replace(block)
            BlockAcceptResult.ConsumedOpen
        }

        else -> BlockAcceptResult.Rejected
    }

    private fun acceptLookup(block: BlockBuilder, stack: BlockBuilderStack): BlockAcceptResult = when (block) {
        is CodeBuilder if (block.kind.isExtension) -> {
            if (block.def.closer == null && def.opener is ScopeToken.Closed)
                stack.problems.reportUnmatchedImplicitExtension(block, this)
            stack.replace(block)
            BlockAcceptResult.ConsumedOpen
        }

        else -> def.opener?.takeAsOrNull<ScopeToken.Lookup>()
            ?.let { acceptLookup(block, it) }
            ?: BlockAcceptResult.Rejected
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

//<editor-fold desc="=#= Problem reports =#=">
private fun FileProblemReporter.bailConsumedRoot(): Nothing = bail(
    LAYOUT_GROUP.child("consumed-root"), 0, 0
) {
    label = "Consumed root scope"
    details = "Parser got hungry and ate the whole AST (っ´ཀ`)っ."
    hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
}

private fun FileProblemReporter.bailUnknownToken(token: AntlrToken): Nothing = bail(
    LAYOUT_GROUP.child("unexpected-token"), token
) {
    label = "Unknown file layout token"
    details = "Token type ${token.type} was not expected to appear ꒰•́ ｡ •̀꒱?."
    hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
}

private fun FileProblemReporter.reportParserError(body: AntlrToken, err: Throwable): Any = report(
    LAYOUT_GROUP.child("parser-failure"), body
) {
    label = "Invalid expression"
    details = "Parser tried to do its job and had a burnout ╮(⊙_ʖ⊙)╭."
    hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
    exception = err
}

private fun FileProblemReporter.reportRootCloser(code: CodeBuilder, parent: BlockBuilder.Scoped): Any = report(
    LAYOUT_GROUP.child("unmatched-root-closer"), code.host.body
) {
    label = "Unmatched ${code.defName} closer"
    details = "${code.defTitle} must have a corresponding `${code.mark.text} ... {` comment."

    val candidate = parent.entries.lastOrNull {
        it !== code && it is CodeBuilder && it.mark.type == code.mark.type && it.host.opener == null
    }
    if (candidate is CodeBuilder) hint = buildString {
        appendLine("${code.defTitle} at this location may need to end with `{`:")
        append(locate(candidate.host.body.endIndex).toLinkString())
    }
}

private fun FileProblemReporter.reportMismatchedCloser(code: CodeBuilder, parent: CodeBuilder): Any = report(
    LAYOUT_GROUP.child("mismatched-closer-type"), code.host.body
) {
    label = "Mismatched closer type"
    details = "${parent.defTitle} can't be closed with a ${code.defTitle} marker."
    hint = buildString {
        appendLine("${parent.defTitle} at this location needs to be closed with `${parent.mark.text}}`:")
        append(locate(parent.host.body.endIndex).toLinkString())
    }
}

private fun FileProblemReporter.reportUnmatchedImplicitExtension(code: CodeBuilder, parent: CodeBuilder): Any = report(
    LAYOUT_GROUP.child("unmatched-implicit-extension"), code.host.body
) {
    label = "Unmatched implicit extension"
    details = "${code.defTitle} doesn't follows a ${parent.defName} ending with `{`."
    hint = "Start this ${code.defName} with `${code.mark.text}}`."
}

private fun FileProblemReporter.reportUnclosedScope(code: CodeBuilder, opener: StitcherToken): Any = report(
    LAYOUT_GROUP.child("unclosed-scope"), opener
) {
    label = "Unclosed ${code.defName}"
    details = "Missing closing `${code.mark.text}}` comment."
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
//</editor-fold>

private fun BlockBuilder.Scoped.findLineConditionCandidate(): CodeBuilder? {
    for (block in entries.asReversed()) when (block) {
        is CodeBuilder if (block.def is DefinitionToken.Condition && block.kind.isOpen) -> return block
        else -> if (block.isNotBlank()) break
    }
    return null
}

private fun BlockBuilder.Scoped.mergeDanglingBlocks(target: CodeBuilder) {
    val iter = entries.run { listIterator(size) }
    while (iter.hasPrevious()) when (val it = iter.previous()) {
        target -> break
        else -> target.entries += it.also { iter.remove() }
    }
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

private val CodeBuilder.defTitle: String
    inline get() = defName.titlecase()

private val CodeBuilder.defName: String
    get() = when (mark.type) {
        StitcherLexer.COND_MARK -> "condition"
        StitcherLexer.SWAP_MARK -> "swap"
        StitcherLexer.REPL_MARK -> "replacement"
        else -> "definition"
    }

