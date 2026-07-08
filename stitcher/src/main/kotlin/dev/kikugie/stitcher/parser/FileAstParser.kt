package dev.kikugie.stitcher.parser

import dev.kikugie.antlr.util.LC
import dev.kikugie.antlr.util.advance
import dev.kikugie.antlr.util.endIndex
import dev.kikugie.commons.takeAsOrNull
import dev.kikugie.stitcher.antlr.*
import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.document.locate
import dev.kikugie.stitcher.issue.*
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.impl.*
import dev.kikugie.stitcher.token.impl.DefinitionToken.Kind
import dev.kikugie.stitcher.token.toLeafToken
import dev.kikugie.stitcher.util.*
import org.antlr.v4.runtime.CommonToken
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.TokenStream
import java.util.*

private val MARKERS: CharArray = charArrayOf('?', '$', '~')

internal class FileAstParser private constructor(
    val document: Document,
    val stream: TokenStream,
    parent: BlockToken.Code? = null
) {
    private val stack: BlockBuilderStack = BlockBuilderStack(document, parent)
    private val listener: StitcherErrorListener = StitcherErrorListener(document)

    fun build(): BlockToken.Root {
        while (stream.LA(1) != AntlrToken.EOF) when (NativeStitcherType.of(stream.LA(1))) {
            NativeStitcherType.CONTENT -> processContent(stream.advance())
            NativeStitcherType.COMMENT_OPEN -> processComment(stream.advance(), stream.advance(), stream.advance())
            else -> document.reportUnknownToken(stream.advance())
        }
        return stack.build()
    }

    private fun processContent(token: AntlrToken) {
        if (token.text.isNotEmpty()) stack.handle(ContentBuilder(token))
    }

    private fun processComment(opener: AntlrToken, body: AntlrToken, closer: AntlrToken) {
        val comment = CommentBuilder(opener, body, closer)
        val code = parseCommentBody(body)
            ?: return stack.handle(comment)
        stack.handle(CodeBuilder(comment, code))
    }

    private fun parseCommentBody(body: AntlrToken): CodeToken? = InlineCharStream(body).use { input ->
        if (input.LC(1) !in MARKERS) return@use null

        val lexer = StitcherLexer(input).apply {
            errorListener = listener
            tokenFactory = InlineTokenFactory(body.lineIndex, body.charPositionInLine)
        }
        val parser = StitcherParser(CommonTokenStream(lexer)).apply {
            errorListener = listener
            errorHandler = StitcherErrorStrategy
        }
        parser.tryBuildDefinition(body)
    }

    private fun StitcherParser.tryBuildDefinition(body: AntlrToken): CodeToken? = try {
        document.runThrowing { definition() }.acceptAs(TokenBuilder, document)
    } catch (_: BailException) {
        null
    } catch (e: RuntimeException) {
        document.reportParserError(body, e)
        null
    }
}

private class BlockBuilderStack(val document: Document, parent: BlockToken.Code?) {
    private val direct: Deque<BlockBuilder.Scoped> = ArrayDeque(4)

    val top: BlockBuilder.Scoped
        get() = direct.peekLast()

    init {
        direct += RootBuilder()
        if (parent != null) direct += unbuild(parent)
    }

    fun build(): BlockToken.Root {
        while (direct.isNotEmpty()) when (val it = pop(true)) {
            is RootBuilder -> return it.build(document)
            else -> {}
        }

        document.bailConsumedRoot()
    }

    fun pop(done: Boolean = false): BlockBuilder.Scoped =
        direct.removeLast().apply { finalize(document, done) }

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

    private fun unbuild(block: BlockToken.Code): CodeBuilder = with(block) {
        val host = CommentBuilder(host.opener?.toAntlrToken(), host.body.toAntlrToken(), host.closer?.toAntlrToken())
        CodeBuilder(host, code)
    }
}

private sealed interface BlockAcceptResult {
    object Rejected : BlockAcceptResult
    object ConsumedOpen : BlockAcceptResult
    object ConsumedFinal : BlockAcceptResult
    class ConsumedPartial(val remainder: BlockBuilder) : BlockAcceptResult
}

private sealed interface BlockBuilder {
    var parent: Scoped
    fun build(document: Document): BlockToken

    sealed interface Scoped : BlockBuilder {
        val entries: MutableList<BlockBuilder>

        fun accept(block: BlockBuilder, stack: BlockBuilderStack): BlockAcceptResult
        fun finalize(problems: ProblemReporter, done: Boolean) {}
    }
}

private class ContentBuilder(val token: AntlrToken) : BlockBuilder {
    override lateinit var parent: BlockBuilder.Scoped
    val text: String by lazy { token.text }

    override fun build(document: Document): BlockToken.Content = BlockToken.Content(
        token.toLeafToken(document, text = text),
    )
}

private class CommentBuilder(val opener: AntlrToken?, val body: AntlrToken, val closer: AntlrToken?) : BlockBuilder {
    override lateinit var parent: BlockBuilder.Scoped
    val text: String by lazy { body.text }

    override fun build(document: Document): BlockToken.Comment = BlockToken.Comment(
        opener?.toLeafToken(document),
        body.toLeafToken(document, text = text),
        closer?.toLeafToken(document),
    )
}

private class RootBuilder(override val entries: MutableList<BlockBuilder> = mutableListOf()) : BlockBuilder.Scoped {
    override var parent: BlockBuilder.Scoped = this
    override fun build(document: Document): BlockToken.Root = BlockToken.Root(
        document,
        entries.map { it.build(document) },
    )

    override fun accept(block: BlockBuilder, stack: BlockBuilderStack): BlockAcceptResult = when (block) {
        is CodeBuilder -> acceptCode(block, stack)
        else -> {
            entries += block.also { it.parent = this }
            BlockAcceptResult.ConsumedOpen
        }
    }

    private fun acceptCode(code: CodeBuilder, stack: BlockBuilderStack): BlockAcceptResult {
        // `}` is always invalid in the root scope
        if (code.def.closer != null) stack.document.reportRootCloser(code, this)

        // For line extensions, try merging empty content into the previous scope
        else if (code.kind.isExtension && code.def.closer == null) with(stack.top) {
            val candidate = findImplicitConditionCandidate()
            if (candidate != null) mergeDanglingBlocks(candidate)
            else stack.document.reportDanglingImplicitExtension(code, this)
        }

        entries += code.also { it.parent = this }
        stack.push(code)
        return BlockAcceptResult.ConsumedOpen
    }
}

private class CodeBuilder(
    val host: CommentBuilder, val token: CodeToken,
    override val entries: MutableList<BlockBuilder> = mutableListOf()
) : BlockBuilder.Scoped {
    override lateinit var parent: BlockBuilder.Scoped
    val mark: LeafToken inline get() = token.marker
    val def: DefinitionToken inline get() = token.definition
    val kind: Kind inline get() = def.kind
    var satisfied: Boolean = kind.isScoped
        private set

    override fun build(document: Document): BlockToken.Code = BlockToken.Code(
        host.build(document), token, entries.map { it.build(document) }
    )

    override fun finalize(problems: ProblemReporter, done: Boolean) = when (val opener = def.opener) {
        is ScopeToken.Lookup if opener.lookup != null && !satisfied -> problems.reportLookupNotFound(this, opener)
        is ScopeToken.Closed if done -> problems.reportUnclosedScope(this, opener)
        else -> Unit
    }

    override fun accept(block: BlockBuilder, stack: BlockBuilderStack): BlockAcceptResult = when (kind) {
        Kind.LINE_OPENER, Kind.LINE_EXTENSION -> acceptLine(block, stack)
        Kind.SCOPED_OPENER, Kind.SCOPED_EXTENSION -> acceptClosed(block, stack)
        Kind.LOOKUP_OPENER, Kind.LOOKUP_EXTENSION -> acceptLookup(block, stack)
        else -> BlockAcceptResult.Rejected
    }

    private fun acceptClosed(block: BlockBuilder, stack: BlockBuilderStack): BlockAcceptResult = when (block) {
        // For line extensions, try merging empty content into the previous scope
        is CodeBuilder if (block.kind.isExtension && block.def.closer == null) -> with(stack.top) {
            val candidate = findImplicitConditionCandidate()
            if (candidate != null) mergeDanglingBlocks(candidate)
            else stack.document.reportDanglingImplicitExtension(block, this)
            stack.push(block)
            BlockAcceptResult.ConsumedOpen
        }

        is CodeBuilder if (block.kind.isCloser) -> {
            if (block notSameAs this)
                stack.document.reportMismatchedCloser(block, this)
            stack.replace(block)
            BlockAcceptResult.ConsumedOpen
        }

        is CodeBuilder -> {
            entries += block.also { it.parent = this }
            stack.push(block)
            BlockAcceptResult.ConsumedOpen
        }

        else -> {
            entries += block.also { it.parent = this }
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
            if (split < 0 && block.isNotBlank() && block.closer?.text.orEmpty().any(LINE_BREAKS::contains))
                split = block.text.length
            consumeCommentSplit(block, split)
        }

        is CodeBuilder if (block.kind.isExtension) -> {
            if (block notSameAs this || block.def.closer == null && def.opener is ScopeToken.Closed)
                stack.document.reportUnmatchedImplicitExtension(block, this)
            stack.replace(block)
            BlockAcceptResult.ConsumedOpen
        }

        else -> BlockAcceptResult.Rejected
    }

    private fun acceptLookup(block: BlockBuilder, stack: BlockBuilderStack): BlockAcceptResult = when (block) {
        is CodeBuilder if (block.kind.isExtension) -> {
            if (block notSameAs this || block.def.closer == null && def.opener is ScopeToken.Closed)
                stack.document.reportUnmatchedImplicitExtension(block, this)
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
            entries += CommonToken(block.token)
                .apply { text = text.take(split); endIndex = startIndex + split }
                .let(::ContentBuilder)
            CommonToken(block.token)
                .apply { text = text.substring(split); startIndex += split }
                .let(::ContentBuilder)
                .let(BlockAcceptResult::ConsumedPartial)
        }
    }

    private fun consumeCommentSplit(block: CommentBuilder, split: Int): BlockAcceptResult = when (split) {
        -1 -> {
            entries += block.also { it.parent = this }
            BlockAcceptResult.ConsumedOpen
        }

        0 -> {
            satisfied = true
            BlockAcceptResult.Rejected
        }

        block.text.length -> consumeFinal(block)

        else -> with(block) {
            satisfied = true

            val partial = CommonToken(body).apply { text = text.take(split); endIndex = startIndex + split }
            entries += CommentBuilder(opener, partial, null).also { it.parent = this@CodeBuilder }

            val remainder = CommonToken(body).apply { text = text.substring(split); startIndex += split }
            CommentBuilder(null, remainder, closer).let(BlockAcceptResult::ConsumedPartial)
        }
    }

    private fun consumeFinal(block: BlockBuilder): BlockAcceptResult {
        satisfied = true
        entries += block.also { it.parent = this }
        return BlockAcceptResult.ConsumedFinal
    }

    private fun isImmediate(): Boolean = entries.any(BlockBuilder::isNotBlank)
}

//<editor-fold desc="=#= Problem reports =#=">
private fun ProblemReporter.bailConsumedRoot(): Nothing = bail(
    LAYOUT_GROUP + "consumed-root"
) {
    label = "Consumed root scope"
    info = "Parser got hungry and ate the whole AST (っ´ཀ`)っ."
    hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
}

private fun ProblemReporter.reportUnknownToken(token: AntlrToken): Unit = report(
    LAYOUT_GROUP + "unexpected-token"
) {
    loc = doc.locate(token)

    label = "Unknown file layout token"
    info = "Token type ${token.type} was not expected to appear ꒰•́ ｡ •̀꒱?."
    hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
}

private fun ProblemReporter.reportParserError(body: AntlrToken, error: Throwable): Unit = report(
    LAYOUT_GROUP + "parser-failure"
) {
    loc = doc.locate(body)
    label = "Invalid expression"
    info = "Parser tried to do its job and had a burnout ╮(⊙_ʖ⊙)╭."
    hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
    err = error
}

private fun ProblemReporter.reportRootCloser(code: CodeBuilder, parent: BlockBuilder.Scoped): Unit = report(
    LAYOUT_GROUP + "unmatched-root-closer"
) {
    loc = doc.locate(code.host.body)
    label = "Unmatched ${code.defName} closer"
    info = "${code.defTitle} must have a corresponding `${code.mark.text} ... {` comment."

    val candidate = parent.findRootCloserCandidate(code)
    if (candidate != null) hint = buildString {
        appendLine("${code.defTitle} at this location may need to end with `{`:")
        append(doc.locate(candidate.host.body.endIndex).link())
    }
}

private fun ProblemReporter.reportMismatchedCloser(code: CodeBuilder, parent: CodeBuilder): Unit = report(
    LAYOUT_GROUP + "mismatched-closer-type"
) {
    loc = doc.locate(code.host.body)
    label = "Mismatched closer type"
    info = "${parent.defTitle} can't be closed with a ${code.defTitle} marker."
    hint = buildString {
        appendLine("${parent.defTitle} at this location needs to be closed with `${parent.mark.text}}`:")
        append(doc.locate(parent.host.body.endIndex).link())
    }
}

private fun ProblemReporter.reportUnmatchedImplicitExtension(code: CodeBuilder, parent: BlockBuilder.Scoped): Unit = report(
    LAYOUT_GROUP + "unmatched-implicit-extension"
) {
    loc = doc.locate(code.host.body)
    label = "Unmatched extension"
    info = "${code.defTitle} doesn't follow a ${parent.defName} ending with `{`."
    hint = "Start this ${code.defName} with `${code.mark.text}}`."
}

private fun ProblemReporter.reportDanglingExtensionCandidate(code: CodeBuilder, candidate: BlockBuilder): Unit = report(
    LAYOUT_GROUP + "dangling-implicit-extension"
) {
    loc = doc.locate(code.host.body)
    label = "Dangling implicit extension"
    info = "${code.defTitle} doesn't follow a valid ${code.defName}."
    hint = buildString {
        appendLine("${candidate.defTitle} at this location prevents the ${code.defName} from extending the chain:")
        append(doc.locate(candidate.blamePosition).link())
    }
}

private fun ProblemReporter.reportDanglingImplicitExtension(code: CodeBuilder, parent: BlockBuilder.Scoped) {
    val candidate = parent.findImplicitExtensionBarrier(code.kind.isLookup)
    if (candidate == null) reportUnmatchedImplicitExtension(code, parent)
    else reportDanglingExtensionCandidate(code, candidate)
}

private fun ProblemReporter.reportUnclosedScope(code: CodeBuilder, opener: StitcherToken): Unit = report(
    LAYOUT_GROUP + "unclosed-scope"
) {
    loc = doc.locate(opener)
    label = "Unclosed ${code.defName}"
    info = "Missing closing `${code.mark.text}}` comment."
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#closed"
}

private fun ProblemReporter.reportLookupNotFound(code: CodeBuilder, opener: ScopeToken.Lookup): Unit = report(
    LAYOUT_GROUP + "lookup-not-found"
) {
    loc = doc.locate(opener)
    label = "Lookup string not found"
    info = "Pattern '${opener.lookup?.unquote()}' wasn't found in the text ahead."
    docs = "https://stonecutter.kikugie.dev/wiki/config/params#lookup"

    // FIXME: it's built before the list of blocks is complete
    val candidate = code.parent.findLookupMatchBarrier(code)
    if (candidate != null) hint = buildString {
        appendLine("${candidate.defTitle} at this position stops the search:")
        append(doc.locate(candidate.blamePosition).link())
    }
}
//</editor-fold>

private fun BlockBuilder.Scoped.findRootCloserCandidate(target: CodeBuilder): CodeBuilder? {
    for (block in entries.asReversed()) when (block) {
        is CodeBuilder if (block sameAs target && block.kind.isLine) -> return block
        is CodeBuilder if (block sameAs target) -> break
        else -> {}
    }
    return null
}

private fun BlockBuilder.Scoped.findImplicitExtensionBarrier(lookup: Boolean): BlockBuilder? {
    var candidate: BlockBuilder? = null
    for (block in entries.asReversed()) when (block) {
        is CodeBuilder if (block.def is DefinitionToken.Condition && block.kind.isOpen) -> break
        is CodeBuilder -> return null
        else -> if (lookup || block.isNotBlank()) candidate = block
    }
    return candidate
}

private fun BlockBuilder.Scoped.findLookupMatchBarrier(target: CodeBuilder): BlockBuilder? = when (val index = entries.indexOf(target)) {
    -1 -> null
    entries.lastIndex -> target.parent.takeAsOrNull<CodeBuilder>()
    else -> entries[index + 1]
}


private fun BlockBuilder.Scoped.findImplicitConditionCandidate(): CodeBuilder? {
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

@Suppress("NOTHING_TO_INLINE")
private inline infix fun CodeBuilder.notSameAs(other: CodeBuilder): Boolean =
    mark.type != other.mark.type

@Suppress("NOTHING_TO_INLINE")
private inline infix fun CodeBuilder.sameAs(other: CodeBuilder): Boolean =
    mark.type == other.mark.type

private fun BlockBuilder.isNotBlank(): Boolean = when (this) {
    is ContentBuilder -> text.isNotBlank()
    is CommentBuilder -> text.isNotBlank()
    is RootBuilder -> entries.all(BlockBuilder::isNotBlank)
    is CodeBuilder -> false
}

private val BlockBuilder.defTitle: String
    inline get() = defName.titlecase()

private val BlockBuilder.defName: String
    get() = when (this) {
        is ContentBuilder -> "text"
        is CommentBuilder -> "comment"
        is CodeBuilder -> defName
        else -> ""
    }

private val CodeBuilder.defTitle: String
    inline get() = defName.titlecase()

private val CodeBuilder.defName: String
    get() = when (mark.type.value) {
        Tokens.COND_MARK -> "condition"
        Tokens.SWAP_MARK -> "swap"
        Tokens.REPL_MARK -> "replacement"
        else -> "definition"
    }

private val BlockBuilder.blamePosition: Int
    get() = when (this) {
        is ContentBuilder -> token.startIndex
        is CommentBuilder -> body.startIndex
        is CodeBuilder -> host.opener!!.startIndex
        else -> 0
    }

private fun LeafToken.toAntlrToken(): AntlrToken = CommonToken(type.value, text).apply {
    startIndex = start
    endIndex = end
}
