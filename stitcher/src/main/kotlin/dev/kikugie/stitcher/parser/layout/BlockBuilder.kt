package dev.kikugie.stitcher.parser.layout

import dev.kikugie.commons.text.countMatching
import dev.kikugie.commons.text.countWhile
import dev.kikugie.commons.then
import dev.kikugie.stitcher.antlr.AntlrToken
import dev.kikugie.stitcher.antlr.endIndex
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.parser.LAYOUT_GROUP
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.fromAntlrToken
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.DefinitionToken.Kind
import dev.kikugie.stitcher.token.impl.ScopeToken
import dev.kikugie.stitcher.util.*
import org.antlr.v4.runtime.CommonToken
import kotlin.math.max
import kotlin.math.min

/**
 * Mutable version of [BlockToken] used in [LayoutParser].
 */
internal sealed interface BlockBuilder {
    val factory: StitcherTokenFactory

    fun build(): BlockToken

    sealed interface Scoped : BlockBuilder {
        val entries: List<BlockBuilder>
        fun accept(block: BlockBuilder): BlockAcceptResult
        fun finalize(problems: FileProblemReporter, isEOF: Boolean): Unit = Unit
    }
}

internal class ContentBuilder(
    override val factory: StitcherTokenFactory,
    val start: Int,
    val end: Int,
    val text: String
) : BlockBuilder {
    constructor(factory: StitcherTokenFactory, content: AntlrToken)
        : this(factory, content.startIndex, content.endIndex, content.text)

    override fun build(): BlockToken.Content = BlockToken.Content(
        factory.create(StitcherType.Composite.CONTENT, start, end, text)
    )
}

internal class CommentBuilder(
    override val factory: StitcherTokenFactory,
    val opener: AntlrToken?,
    val body: AntlrToken,
    val closer: AntlrToken?,
) : BlockBuilder {
    val text inline get() = body.text
    override fun build(): BlockToken.Comment = BlockToken.Comment(
        opener?.let { factory.fromAntlrToken(it, StitcherType.Layout) },
        body.let { factory.fromAntlrToken(it, StitcherType.Layout) },
        closer?.let { factory.fromAntlrToken(it, StitcherType.Layout) },
    )
}

internal class RootBuilder(
    override val factory: StitcherTokenFactory,
    override val entries: MutableList<BlockBuilder> = mutableListOf()
) : BlockBuilder.Scoped {
    override fun build(): BlockToken.Root = BlockToken.Root(
        entries.map(BlockBuilder::build)
    )

    override fun accept(block: BlockBuilder): BlockAcceptResult =
        entries.addMerging(block) then BlockAcceptResult.ConsumedOpen
}

internal class CodeBuilder(
    override val factory: StitcherTokenFactory,
    val source: CommentBuilder,
    val marker: AntlrToken,
    val definition: DefinitionToken,
    override val entries: MutableList<BlockBuilder> = mutableListOf()
) : BlockBuilder.Scoped {
    val kind: Kind inline get() = definition.kind
    var satisfied: Boolean = kind.isScoped
        private set

    override fun build(): BlockToken.Code {
        val host = source.build()
        val blocks = entries.map(BlockBuilder::build)
        return BlockToken.Code(host, factory.fromAntlrToken(marker, StitcherType.Leaf), definition, blocks)
    }

    override fun accept(block: BlockBuilder): BlockAcceptResult = when (definition.kind) {
        Kind.SCOPED_OPENER, Kind.SCOPED_EXTENSION -> entries.addMerging(block) then BlockAcceptResult.ConsumedOpen
        Kind.LINE_OPENER, Kind.LINE_EXTENSION -> if (satisfied) BlockAcceptResult.Rejected else acceptLine(block)
        Kind.LOOKUP_OPENER, Kind.LOOKUP_EXTENSION -> if (satisfied) BlockAcceptResult.Rejected else acceptWord(block)
        else -> BlockAcceptResult.Rejected
    }

    override fun finalize(problems: FileProblemReporter, isEOF: Boolean): Unit = when (val opener = definition.opener) {
        is ScopeToken.Closed if isEOF -> problems.report(LAYOUT_GROUP.child("unclosed-scope"), opener) {
            label = "Unclosed scope"
            details = "Missing closing `${marker.text}}` comment."
            documentation = "https://stonecutter.kikugie.dev/wiki/config/params#closed"
        }

        is ScopeToken.Lookup if opener.lookup != null && !satisfied -> problems.report(LAYOUT_GROUP.child("lookup-not-found"), opener) {
            label = "Lookup string not found"
            details = "The pattern '${opener.extractLookup()}' wasn't found in the text ahead. " +
                "Note: the pattern search is interrupted by any condition, swap or replacement comments."
            documentation = "https://stonecutter.kikugie.dev/wiki/config/params#lookup"
        }

        else -> {}
    }.let { }

    private fun acceptLine(block: BlockBuilder): BlockAcceptResult = when (block) {
        is ContentBuilder -> consumeContentSplit(block, consumeLine(block.text, entries.lastOrNull().isNotBlank()))
        is CommentBuilder -> consumeCommentSplit(block, consumeLine(block.text, entries.lastOrNull().isNotBlank())).let {
            if (it !is BlockAcceptResult.ConsumedOpen) it
            else if (!block.text.isNotBlank() || !block.closer?.text.orEmpty().hasLineBreak()) it
            else BlockAcceptResult.ConsumedFinal
        }

        else -> consumeFinal(block)
    }

    private fun acceptWord(block: BlockBuilder): BlockAcceptResult =
        if (block is BlockBuilder.Scoped) consumeFinal(block)
        else acceptWord(block, definition.opener as ScopeToken.Lookup)

    private fun acceptWord(block: BlockBuilder, scope: ScopeToken.Lookup): BlockAcceptResult = when (block) {
        is ContentBuilder -> consumeContentSplit(
            block,
            if (scope.lookup == null) consumeWordDefault(block.text)
            else consumeWordCustom(block.text, scope.extractLookup(), scope.isCapturing())
        )

        is CommentBuilder -> consumeCommentSplit(
            block,
            if (scope.lookup == null) consumeWordDefault(block.text)
            else consumeWordCustom(block.text, scope.extractLookup(), scope.isCapturing())
        )

        else -> error("Unreachable")
    }

    private fun consumeFinal(block: BlockBuilder): BlockAcceptResult {
        satisfied = true
        return entries.addMerging(block) then BlockAcceptResult.ConsumedFinal
    }

    private fun consumeContentSplit(block: ContentBuilder, split: Int): BlockAcceptResult = when {
        // No end sequence was found
        split < 0 -> entries.addMerging(block) then BlockAcceptResult.ConsumedOpen

        // The matching sequence is right at the start of the block, but we don't want it
        split == 0 -> {
            satisfied = true
            BlockAcceptResult.Rejected
        }

        // The entire content was consumed
        split == block.text.length -> consumeFinal(block)

        else -> {
            satisfied = true
            // Block before the split goes to this scope
            ContentBuilder(factory, block.start, block.start + split, block.text.take(split))
                .let(entries::addMerging)
            // Return the remainder
            ContentBuilder(factory, block.start + split, block.end, block.text.substring(split))
                .let(BlockAcceptResult::ConsumedPartial)
        }
    }

    private fun consumeCommentSplit(block: CommentBuilder, split: Int): BlockAcceptResult = when {
        // No end sequence was found
        split < 0 -> entries.add(block) then BlockAcceptResult.ConsumedOpen

        // The entire comment was consumed
        split == block.text.length -> consumeFinal(block)

        else -> {
            satisfied = true
            // Split comment without a closer goes to this scope
            val partial = CommonToken(block.body).apply {
                text = text.take(split)
                stopIndex = startIndex + split - 1
            }
            CommentBuilder(factory, block.opener, partial, null)
                .let(entries::add)
            // Return the remainder comment without an opener
            val remainder = CommonToken(block.body).apply {
                text = text.substring(split)
                startIndex += split
            }
            CommentBuilder(factory, null, remainder, block.closer)
                .let(BlockAcceptResult::ConsumedPartial)
        }
    }
}

private fun MutableList<BlockBuilder>.addMerging(block: BlockBuilder) = when (val it = lastOrNull()) {
    is ContentBuilder if (block is ContentBuilder) ->
        this[lastIndex] = ContentBuilder(it.factory, min(it.start, block.start), max(it.end, block.end), it.text + block.text)

    else -> this += block
}

private fun consumeLine(str: String, immediate: Boolean): Int {
    var state = 0
    val index = str.countWhile { ch ->
        matchLineChar(ch, state, immediate).also { if (it >= 0) state = it } >= 0
    }
    // -1 indicates we didn't reach a newline
    return if (index == str.length && state == 0) -1 else index
}

private fun matchLineChar(ch: Char, state: Int, immediate: Boolean): Int = when (state) {
    0 -> when (ch) {
        in WORD_BREAKS -> 0
        in LINE_BREAKS -> if (immediate) 1 else 0
        else -> 1
    }

    1 -> when (ch) {
        '\n' -> 2
        '\r' -> 3
        else -> 1
    }

    2 -> when (ch) {
        '\r' -> 3
        else -> -1
    }

    else -> -1
}

private fun consumeWordDefault(str: String): Int = when (val idx = str.countMatching(*WHITESPACES)) {
    str.length -> -1
    else -> idx + str.countWhile(idx) { it !in WHITESPACES }
}

private fun consumeWordCustom(str: String, match: String, capturing: Boolean): Int = when (val idx = str.indexOf(match)) {
    -1 -> -1
    else if capturing -> idx + match.length
    else -> idx
}

private fun BlockBuilder?.isNotBlank() = when (this) {
    is ContentBuilder -> text.isNotBlank()
    is CommentBuilder -> text.isNotBlank()
    null -> false
    else -> true
}