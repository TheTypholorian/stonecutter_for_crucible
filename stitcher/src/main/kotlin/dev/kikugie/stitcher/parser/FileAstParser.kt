package dev.kikugie.stitcher.parser

import dev.kikugie.commons.then
import dev.kikugie.stitcher.antlr.StitcherLexer.Tokens
import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.issue.*
import dev.kikugie.stitcher.token.LAYOUT_GROUP
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.DefinitionToken.Kind
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.token.impl.ScopeToken
import dev.kikugie.stitcher.util.LINE_BREAKS
import dev.kikugie.stitcher.util.WHITESPACES
import dev.kikugie.stitcher.util.titlecase
import dev.kikugie.stitcher.util.unquote

object FileAstParser {
    fun parse(stream: FileTokenStream): BlockToken.Root {
        val stack = ScopeBuilderStack(stream.document)
        for (token in stream)
            stack.accept(token.accept(BlockWrapper, null))
        return stack.build(stream.document)
    }
}

@JvmInline
private value class ScopeBuilderStack private constructor(val deque: ArrayDeque<ScopeBuilder>) {
    constructor(document: Document) : this(ArrayDeque(4)) {
        deque += RootBuilder(document)
    }

    val first: RootBuilder inline get() = deque.first() as RootBuilder
    val last: ScopeBuilder inline get() = deque.last()

    fun pop(by: ScopeBuilder? = null): ScopeBuilder = when (deque.size) {
        1 -> first.document.reportConsumedRoot(by) then last
        else -> deque.removeLast()
    }

    fun push(scope: CodeBuilder): Unit =
        deque.addLast(scope)

    fun replace(scope: CodeBuilder): ScopeBuilder =
        pop(by = scope).also { push(scope) }

    fun build(document: Document): BlockToken.Root {
        while (deque.isNotEmpty()) when (val it = pop()) {
            is RootBuilder -> return it.stub.copy(scope = buildList { it.build(this) })
            else -> {}
        }

        document.reportConsumedRoot()
        throw BailException()
    }

    tailrec fun accept(scope: ScopeBuilder): Unit = when (val it = last.accept(scope, this)) {
        // Can accept more – do nothing
        AcceptResult.Consumed -> {}

        // Can't accept more – pop the scope
        AcceptResult.Closed -> {
            pop(by = scope)
            run {}
        }

        // Can't accept this – pop the scope and try again
        AcceptResult.Rejected -> {
            pop(by = scope)
            accept(scope)
        }

        // Can't accept more – pop the scope and accept the remainder
        is AcceptResult.Partial -> {
            pop(by = scope)
            accept(it.remainder)
        }
    }
}

private sealed interface AcceptResult {
    object Rejected : AcceptResult
    object Consumed : AcceptResult
    object Closed : AcceptResult
    class Partial(val remainder: ScopeBuilder) : AcceptResult
}

private sealed interface ScopeBuilder {
    val stub: BlockToken
    val entries: MutableList<ScopeBuilder>
        get() = throw UnsupportedOperationException()

    fun build(collector: MutableList<BlockToken>): Unit = collector.plusAssign(stub)
    fun accept(builder: ScopeBuilder, stack: ScopeBuilderStack): AcceptResult = AcceptResult.Rejected
}

private object BlockWrapper : BlockToken.Visitor<ScopeBuilder, Nothing?> {
    override fun visitContent(content: BlockToken.Content, ctx: Nothing?): ScopeBuilder =
        ContentBuilder(content)

    override fun visitComment(comment: BlockToken.Comment, ctx: Nothing?): ScopeBuilder =
        CommentBuilder(comment)

    override fun visitRoot(root: BlockToken.Root, ctx: Nothing?): ScopeBuilder =
        RootBuilder(root.document)

    override fun visitCode(code: BlockToken.Code, ctx: Nothing?): ScopeBuilder = when (code.marker.type.value) {
        Tokens.COND_MARK -> ConditionBuilder(code)
        Tokens.SWAP_MARK -> SwapBuilder(code)
        Tokens.REPL_MARK -> ReplacementBuilder(code)
        else -> error("Invalid definition type ${code.marker.type}")
    }
}

private class ContentBuilder(override val stub: BlockToken.Content) : ScopeBuilder {
    val text by lazy { stub.leaf.text }
    val blank: Boolean by lazy { text.isBlank() }

    override fun toString(): String = "ContentBuilder(stub=$stub)"
}

private class CommentBuilder(override val stub: BlockToken.Comment) : ScopeBuilder {
    val text by lazy { stub.body.text }
    val blank: Boolean by lazy { text.isBlank() }

    override fun toString(): String = "CommentBuilder(stub=$stub)"
}

private class RootBuilder(override val stub: BlockToken.Root) : ScopeBuilder {
    constructor(document: Document) : this(BlockToken.Root(document, emptyList()))
    override val entries: MutableList<ScopeBuilder> = ArrayList()

    override fun toString(): String = "RootBuilder(stub=$stub)"

    override fun build(collector: MutableList<BlockToken>) {
        for (it in entries) it.build(collector)
    }

    override fun accept(builder: ScopeBuilder, stack: ScopeBuilderStack): AcceptResult {
        if (builder is CodeBuilder) return acceptCode(builder, stack)

        entries += builder
        return AcceptResult.Consumed
    }

    private fun acceptCode(builder: CodeBuilder, stack: ScopeBuilderStack): AcceptResult {
        if (builder.kind.isCloser) document.reportRootCloser(builder)

        entries += builder
        if (!builder.kind.isEmpty) stack.push(builder)
        return AcceptResult.Consumed
    }
}

private sealed class CodeBuilder(override val stub: BlockToken.Code) : ScopeBuilder {
    override val entries: MutableList<ScopeBuilder> = ArrayList()

    val kind: Kind inline get() = definition.kind
    val marker: LeafToken inline get() = stub.marker
    open val definition: DefinitionToken get() = stub.definition
    open val next: CodeBuilder? get() = null

    protected var effectiveScopeSize: Int = 0
    protected var eagerLookupMatch: Boolean = false

    override fun toString(): String = "CodeBuilder(stub=$stub)"

    override fun build(collector: MutableList<BlockToken>) {
        // TODO: limit entries
        collector += stub.copy(scope = buildList { for (it in entries) it.build(this) })
        next?.build(collector)
    }

    override fun accept(builder: ScopeBuilder, stack: ScopeBuilderStack): AcceptResult = when {
        kind.isEmpty -> AcceptResult.Rejected
        else -> acceptMatch(builder, stack)
    }

    open operator fun plusAssign(builder: ScopeBuilder) {
        entries += builder
        eagerLookupMatch = eagerLookupMatch || builder.isNotBlank()
    }

    protected open fun acceptMatch(builder: ScopeBuilder, stack: ScopeBuilderStack): AcceptResult = when (builder) {
        is ContentBuilder -> acceptContent(builder, stack)
        is CommentBuilder -> acceptComment(builder, stack)
        is CodeBuilder -> acceptCode(builder, stack)
        else -> AcceptResult.Rejected
    }

    protected open fun acceptContent(builder: ContentBuilder, stack: ScopeBuilderStack): AcceptResult = when {
        kind.isScoped -> plusAssign(builder) then AcceptResult.Consumed
        kind.isOpen && effectiveScopeSize > 0 -> consumeContentSplit(builder)
        else -> AcceptResult.Rejected
    }

    protected open fun acceptComment(builder: CommentBuilder, stack: ScopeBuilderStack): AcceptResult = when {
        kind.isScoped -> plusAssign(builder) then AcceptResult.Consumed
        kind.isOpen && effectiveScopeSize > 0 -> consumeCommentSplit(builder)
        else -> AcceptResult.Rejected
    }

    protected abstract fun acceptCode(builder: CodeBuilder, stack: ScopeBuilderStack): AcceptResult

    protected fun consumeFinal(builder: ScopeBuilder): AcceptResult {
        this += builder
        effectiveScopeSize = entries.size
        return AcceptResult.Closed
    }

    protected fun consumeContentSplit(builder: ContentBuilder, split: Int = findScopeSplit(builder, eagerLookupMatch)): AcceptResult = when (split) {
        -1 -> plusAssign(builder) then AcceptResult.Consumed
        0 -> AcceptResult.Rejected.also { effectiveScopeSize = entries.size }
        builder.text.length -> consumeFinal(builder)

        else -> with(builder) {
            val leaf = stub.leaf

            this@CodeBuilder += leaf.copy(end = leaf.start + split)
                .apply { text = text.take(split) }
                .let { ContentBuilder(BlockToken.Content(it)) }
            effectiveScopeSize = entries.size
            leaf.copy(start = leaf.start + split)
                .apply { text = text.drop(split) }
                .let { AcceptResult.Partial(ContentBuilder(BlockToken.Content(it))) }
        }
    }

    protected fun consumeCommentSplit(builder: CommentBuilder, split: Int = findScopeSplit(builder, eagerLookupMatch)): AcceptResult = when (split) {
        -1 -> plusAssign(builder) then AcceptResult.Consumed
        0 -> AcceptResult.Rejected.also { effectiveScopeSize = entries.size }
        builder.text.length -> consumeFinal(builder)

        else -> with(builder) {
            val body = stub.body

            this@CodeBuilder += body.copy(end = body.start + split)
                .apply { text = text.take(split) }
                .let { CommentBuilder(stub.copy(body = it, closer = null)) }
            effectiveScopeSize = entries.size
            body.copy(start = body.start + split)
                .apply { text = text.drop(split) }
                .let { AcceptResult.Partial(CommentBuilder(stub.copy(body = it, opener = null))) }
        }
    }
}

private class ConditionBuilder(stub: BlockToken.Code) : CodeBuilder(stub) {
    override val definition: DefinitionToken.Condition get() = super.definition as DefinitionToken.Condition
    override var next: ConditionBuilder? = null

    override fun acceptContent(builder: ContentBuilder, stack: ScopeBuilderStack): AcceptResult =
        postAppendResult(builder, super.acceptContent(builder, stack))

    override fun acceptComment(builder: CommentBuilder, stack: ScopeBuilderStack): AcceptResult =
        postAppendResult(builder, super.acceptComment(builder, stack))

    override fun acceptCode(builder: CodeBuilder, stack: ScopeBuilderStack): AcceptResult = when {
        kind.isScoped -> consumeClosed(builder, stack)
        kind.isOpen -> consumeOpen(builder, stack)
        else -> AcceptResult.Rejected
    }

    private fun consumeClosed(builder: CodeBuilder, stack: ScopeBuilderStack): AcceptResult = when (builder) {
        // `?}` / `? else` statement - chain it and replace the stack head
        is ConditionBuilder if (builder.kind.isCloser) -> {
            // `? else` is probably missing `}`, so it's added anyway
            if (builder.definition.closer == null) document.reportMissingClosingBracket(builder)

            next = builder.also(stack::replace)
            AcceptResult.Consumed
        }

        else -> {
            // It's a closer of a different type, and cross-scopes are not allowed
            if (builder.kind.isCloser) document.reportInvalidCloserType(builder)

            // If it's not a single statement, like replacements, push it up
            if (builder.kind.isOpener) stack.push(builder)

            this += builder
            AcceptResult.Consumed
        }
    }

    private fun consumeOpen(builder: CodeBuilder, stack: ScopeBuilderStack): AcceptResult = when (builder) {
        // `? else` statement - chain it and replace the stack head; scoped closers are pushed down
        is ConditionBuilder if (builder.kind.isCloser && builder.definition.closer == null) -> {
            next = builder.also(stack::replace)
            AcceptResult.Consumed
        }

        // Allows nesting open conditions, but only with blank text in-between
        else if (!eagerLookupMatch) -> {
            // TODO: Check if lookup match was found

            this += builder.also(stack::push)
            AcceptResult.Consumed
        }

        else -> AcceptResult.Rejected
    }
}

private class ReplacementBuilder(stub: BlockToken.Code) : CodeBuilder(stub) {
    override val definition: DefinitionToken.Replacement get() = super.definition as DefinitionToken.Replacement
    override var next: ReplacementBuilder? = null

    override fun acceptCode(builder: CodeBuilder, stack: ScopeBuilderStack): AcceptResult = when {
        kind.isScoped -> consumeClosed(builder, stack)
        kind.isOpen -> consumeOpen(builder, stack)
        else -> AcceptResult.Rejected
    }

    private fun consumeClosed(builder: CodeBuilder, stack: ScopeBuilderStack): AcceptResult = when (builder) {
        is ReplacementBuilder if (builder.kind.isCloser) -> {
            next = builder.also(stack::replace)
            AcceptResult.Consumed
        }

        else -> {
            this += builder
            AcceptResult.Consumed
        }
    }

    private fun consumeOpen(builder: CodeBuilder, stack: ScopeBuilderStack): AcceptResult = when (builder) {
        // Allows nesting open conditions, but only with blank text in-between
        else if (!eagerLookupMatch) -> {
            // TODO: Check if lookup match was found

            this += builder.also(stack::push)
            AcceptResult.Consumed
        }

        else -> AcceptResult.Rejected
    }
}

private class SwapBuilder(stub: BlockToken.Code) : CodeBuilder(stub) {
    override val definition: DefinitionToken.Swap get() = super.definition as DefinitionToken.Swap
    override var next: SwapBuilder? = null

    override fun acceptCode(builder: CodeBuilder, stack: ScopeBuilderStack): AcceptResult = when (builder) {
        is SwapBuilder if (kind.isScoped && builder.kind.isCloser) -> {
            next = builder
            AcceptResult.Consumed
        }

        else if (kind.isScoped) -> {
            this += builder
            AcceptResult.Consumed
        }

        else -> AcceptResult.Rejected
    }
}

//<editor-fold desc="=#= Problem reports =#=">
private fun ProblemReporter.reportConsumedRoot(location: ScopeBuilder? = null): Unit = report(LAYOUT_GROUP + "consumed-root") {
    if (location != null) loc = doc.locate(location.blame)
    label = "Consumed root scope"
    info = "Parser got hungry and ate the whole AST (っ´ཀ`)っ."
    hint = """
        Please report this to https://codeberg.org/stonecutter/stonecutter/issues
        with the problematic file and stacktrace attached.
    """.trimIndent()
}

context(parent: RootBuilder)
private fun ProblemReporter.reportRootCloser(code: CodeBuilder): Unit = report(LAYOUT_GROUP + "unmatched-root-closer") {
    loc = doc.locate(code.blame)
    label = "Unmatched ${code.name} closer"
    info = "${code.title} must have a corresponding `${code.marker.text} ... {` comment."

    val candidate = parent.findRootCloserCandidate(code)
    if (candidate != null) hint = buildString {
        appendLine("${code.title} at this location may need to end with `{`:")
        append(doc.locate(candidate.stub.host.body.end).link())
    }
}

context(parent: CodeBuilder)
private fun ProblemReporter.reportSwapNesting(code: CodeBuilder): Unit = report(LAYOUT_GROUP + "swap-nesting") {
    loc = doc.locate(code.blame)
    label = "Ignored ${code.name}"
    info = "${code.title}s and other statements are not processed inside ${parent.name} scopes."
    hint = buildString {
        appendLine("${parent.title} scope is defined at this location:")
        append(doc.locate(parent.blame).link())
    }
    sev = ProblemSeverity.WARN
}

context(parent: CodeBuilder)
private fun ProblemReporter.reportInvalidCloserType(code: CodeBuilder): Unit = report(LAYOUT_GROUP + "mismatched-closer-type") {
    loc = doc.locate(code.blame)
    label = "Mismatched closer type"
    info = "${parent.title} can't be closed with a ${code.title} marker."
    hint = buildString {
        appendLine("${parent.title} at this location needs to be closed with `${parent.marker.text}}`:")
        append(doc.locate(parent.blame).link())
    }
}

context(parent: CodeBuilder)
private fun ProblemReporter.reportMissingClosingBracket(code: CodeBuilder): Unit = report(LAYOUT_GROUP + "mismatched-extension-closer") {
    loc = doc.locate(code.blame)
    label = "Unmatched extension"
    info = "${code.title} doesn't follow a line or lookup ${parent.name}."
    hint = "Start this ${code.name} with `${code.marker.text}}`."
}
//</editor-fold>

private fun RootBuilder.findRootCloserCandidate(target: CodeBuilder): CodeBuilder? {
    for (block in entries.asReversed()) when (block) {
        is CodeBuilder if (block sameAs target && block.kind.isLine) -> return block
        is CodeBuilder if (block sameAs target) -> break
        else -> {}
    }
    return null
}

// If the remainder is empty, append it anyway, so following statements can be chained
private fun CodeBuilder.postAppendResult(builder: ScopeBuilder, result: AcceptResult): AcceptResult = when (result) {
    AcceptResult.Consumed, AcceptResult.Consumed ->
        AcceptResult.Consumed

    AcceptResult.Rejected if (builder.isBlank()) ->
        plusAssign(builder) then AcceptResult.Consumed

    is AcceptResult.Partial if (result.remainder.isBlank()) ->
        plusAssign(builder) then AcceptResult.Consumed

    else -> result
}

private fun CodeBuilder.findScopeSplit(content: ContentBuilder, immediate: Boolean): Int = when (val op = definition.opener) {
    is ScopeToken.Lookup -> op.lookup?.unquote().let {
        if (it == null) content.text.findWordSplit()
        else content.text.findLookupSplit(it, op.plus != null)
    }

    null -> content.text.findLineSplit(immediate)

    else -> -1
}

private fun CodeBuilder.findScopeSplit(comment: CommentBuilder, immediate: Boolean): Int = when (val op = definition.opener) {
    is ScopeToken.Lookup -> op.lookup?.unquote().let {
        if (it == null) comment.text.findWordSplit()
        else comment.text.findLookupSplit(it, op.plus != null)
    }

    null -> comment.text.findLineSplit(immediate).let {
        if (it >= 0 || !comment.isNotBlank() || '\n' !in comment.stub.closer?.text.orEmpty()) it
        else comment.text.length
    }

    else -> -1
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

private infix fun CodeBuilder.notSameAs(other: CodeBuilder): Boolean =
    marker.type != other.marker.type

private infix fun CodeBuilder.sameAs(other: CodeBuilder): Boolean =
    marker.type == other.marker.type

private val ScopeBuilder.document: Document
    inline get() = stub.document

private val ScopeBuilder.title: String
    inline get() = name.titlecase()

private val ScopeBuilder.name: String
    get() = when (this) {
        is ContentBuilder -> "text"
        is CommentBuilder -> "comment"
        is ConditionBuilder -> "condition"
        is SwapBuilder -> "swap"
        is ReplacementBuilder -> "replacement"
        is CodeBuilder -> "definition"
        is RootBuilder -> "root"
    }

private val ScopeBuilder.blame: Int
    get() = when (this) {
        is ContentBuilder -> stub.start
        is CommentBuilder -> stub.start
        is CodeBuilder -> marker.start
        is RootBuilder -> 0
    }

private fun ScopeBuilder.isNotBlank(): Boolean =
    !isBlank()

private fun ScopeBuilder.isBlank(): Boolean = when (this) {
    is ContentBuilder -> blank
    is CommentBuilder -> blank
    is CodeBuilder -> false
    is RootBuilder -> entries.isEmpty()
}
