package dev.kikugie.stitcher.transformer

import dev.kikugie.stitcher.antlr.*
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.location
import dev.kikugie.stitcher.issue.runMarked
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.layout.LayoutParser
import dev.kikugie.stitcher.replacement.executor.CompositeReplacementExecutor
import dev.kikugie.stitcher.replacement.executor.EmptyReplacementExecutor
import dev.kikugie.stitcher.replacement.executor.ReplacementExecutor
import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.eval.join
import dev.kikugie.stitcher.token.eval.scopeEnd
import dev.kikugie.stitcher.token.eval.scopeStart
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.util.create
import dev.kikugie.stitcher.util.errorListener
import dev.kikugie.stitcher.util.hasLineBreak
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Pair

internal class BlockTransformer private constructor(
    val params: TransformParameters,
    val factory: StitcherTokenFactory,
    val input: CharStream,
    val problems: FileProblemReporter,
    private val state: TransformerState,
) : BlockToken.Visitor<BlockToken> {
    constructor(params: TransformParameters, factory: StitcherTokenFactory, input: CharStream, problems: FileProblemReporter) :
        this(params, factory, input, problems, TransformerState(params, problems))
    private var visitedEnabledBlock: Boolean = false

    override fun visitRoot(root: BlockToken.Root): BlockToken =
        root.copy(scope = root.scope.map { it.accept(this) })

    override fun visitCode(code: BlockToken.Code): BlockToken =
        code.copy(scope = code.definition.accept(ScopeTransformer(code)))

    override fun visitComment(comment: BlockToken.Comment): BlockToken {
        if (comment.opener != null && comment.closer != null) {
            val text = state.replacer.replace(comment.body.text)
            val body = comment.body.copy(text = text)
            return comment.copy(body = body)
        }

        // If this is a partial comment — recomment it to insert the missing opener/closer
        var body = params.commenter.comment(comment.body.text, false)
            .let(state.replacer::replace)
        val closer = comment.closer?.text.orEmpty()
        if (closer.hasLineBreak()) body += closer

        return BlockToken.Content(factory.create(NativeStitcherType.COMMENT, comment.start, comment.end, body))
    }

    override fun visitContent(content: BlockToken.Content): BlockToken {
        val text = state.replacer.replace(content.leaf.text)
        val leaf = content.leaf.copy(text = text)
        return content.copy(leaf = leaf)
    }

    private fun copy() = BlockTransformer(params, factory, input, problems, state)

    private fun visitScope(tokens: Collection<BlockToken>): List<BlockToken> {
        if (tokens.isEmpty()) return emptyList()
        val copy = this@BlockTransformer.copy()
        return tokens.map { it.accept(copy) }
    }

    private inner class ScopeTransformer(val code: BlockToken.Code) : DefinitionToken.Visitor<List<BlockToken>> {
        override fun visitReplacement(repl: DefinitionToken.Replacement): List<BlockToken> {
            repl.accept(state.checker) // Is it necessary to abort if some identifiers are not present?
            val enable = mutableSetOf<String>()
            val disable = mutableSetOf<String>()
            for ((op, id) in repl.expressions) when(op) {
                null -> enable += id.text
                else -> disable += id.text
            }

            state.replacer.update(enable, disable)
            return emptyList()
        }

        override fun visitSwap(swap: DefinitionToken.Swap): List<BlockToken> {
            if (swap !is DefinitionToken.SwapOpener || !swap.accept(state.checker)) return emptyList()

            val replacement = processTemplate(params.swaps[swap.identifier.text]!!, swap.arguments)
            val content = params.replacer.replace(code.scope.join(), replacement)
                .let(state.replacer::replace)
            val start = code.scope.firstOrNull()?.start ?: code.host.end
            val end = code.scope.lastOrNull()?.end ?: code.host.end

            val token = factory.create(NativeStitcherType.CONTENT, start, end, content)
            return listOf(BlockToken.Content(token))
        }

        override fun visitCondition(cond: DefinitionToken.Condition): List<BlockToken> {
            if (cond !is DefinitionToken.ConditionExtension) visitedEnabledBlock = false
            if (cond is DefinitionToken.ConditionCloser || !cond.accept(state.checker)) return emptyList()

            // In an if-else chain makes the rest of the blocks disabled
            val shouldEnable = cond.expression?.accept(state.evaluator) ?: true
                && !visitedEnabledBlock
            visitedEnabledBlock = shouldEnable || visitedEnabledBlock

            return when {
                shouldEnable -> if (code.scope.isCommented()) uncommentScope() else visitScope(code.scope)
                else -> if (!code.scope.isCommented()) commentScope() else visitScope(code.scope)
            }
        }

        private fun processTemplate(template: String, tokens: List<LeafToken>): String {
            if (tokens.isEmpty()) return template

            var offset = 0
            val builder = StringBuilder(template)
            val insertions = tokens.map {
                if (it.type.value == StitcherLexer.QUOTED) it.text.removeSurrounding("'") else it.text
            }
            for (place in template.findInsertionPlaces())
                offset += place.apply(builder, offset) { insertions[it - 1] }
            return builder.toString()
        }

        private fun uncommentScope(): List<BlockToken> {
            if (code.scope.isEmpty()) return emptyList()

            val stream: TokenStream = BlockUncommenter(params, input, problems, code.scope).let(::CommonTokenStream)
            val factory: StitcherTokenFactory = StitcherTokenFactory.Inline(code.host.end)
            val block: BlockToken.Root = problems.runMarked({ LayoutParser.parse(stream, input, factory, problems) }) {
                return it.scope
            }

            return visitScope(block.scope)
        }

        private fun commentScope(): List<BlockToken> {
            if (code.scope.isEmpty()) return emptyList()

            // TODO: Is reprocessing needed if the reformat task is going to be added?
            val content: String = params.commenter.comment(code.scope.join().let(state.replacer::replace), code.definition.opener == null)
            return listOf(BlockToken.Content(factory.create(NativeStitcherType.CONTENT, code.scopeStart, code.scopeEnd, content)))
        }
    }
}

private class TransformerState(params: TransformParameters, problems: FileProblemReporter) {
    val evaluator: ExpressionEvaluator = ExpressionEvaluator(params)
    val checker: InvalidParameterChecker = InvalidParameterChecker(params, problems)
    // TODO: make it shared
    val replacer: ReplacementExecutor =
        if (params.replacements.isEmpty()) EmptyReplacementExecutor
        else CompositeReplacementExecutor(params.replacements)
}

private class BlockUncommenter(
    val params: TransformParameters,
    val input: CharStream,
    val problems: FileProblemReporter,
    blocks: List<BlockToken>,
    list: MutableList<AntlrToken> = mutableListOf()
) : ListTokenSource(list) {
    private val source: Pair<TokenSource?, CharStream?> = Pair(this, input)

    init {
        for (it in blocks) match(list, it)
    }

    private fun match(tokens: MutableList<AntlrToken>, block: BlockToken): Unit = when (block) {
        is BlockToken.Content -> with(block) {
            tokens += tokenFactory.create(source, leaf, problems)
        }

        is BlockToken.Comment -> with(block) {
            val location = problems.location(body)
            val content = params.uncommenter.uncomment(body.text, opener?.text.orEmpty(), closer?.text.orEmpty())
                .charStream(input.sourceName)
            val scanner = params.adapter.create(content, problems).apply {
                scanner.errorListener(InlineErrorListener(location, problems))
            }
            val stream = InlineTokenStream(source, CommonTokenStream(scanner), location)
            TokenStreamIterator(stream).forEach(tokens::add)
        }

        else -> error("Illegal block type ${block::class.simpleName}")
    }
}

private fun List<BlockToken>.isCommented(): Boolean =
    all { it is BlockToken.Comment || (it is BlockToken.Content && it.isBlank()) }
