package dev.kikugie.stitcher.transformer

import dev.kikugie.stitcher.antlr.*
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.ProblemLocation
import dev.kikugie.stitcher.issue.locate
import dev.kikugie.stitcher.issue.runMarked
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.parser.layout.LayoutParser
import dev.kikugie.stitcher.replacement.executor.CompositeReplacementExecutor
import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.eval.join
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.transformer.visitor.ExpressionEvaluator
import dev.kikugie.stitcher.transformer.visitor.ParameterChecker
import dev.kikugie.stitcher.util.*
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.Pair

class BlockTransformer private constructor(val factory: StitcherTokenFactory, private val state: TransformerStateImpl) :
    BlockToken.Visitor<BlockToken>, TransformerStateV2 by state {
    constructor(
        parameters: TransformParameters,
        problems: FileProblemReporter,
        input: CharStream,
        factory: StitcherTokenFactory = StitcherTokenFactory,
    ) : this(factory, TransformerStateImpl(parameters, problems, input))
    internal var conditionCarry: Boolean = false

    override fun visitRoot(root: BlockToken.Root): BlockToken =
        root.copy(scope = root.scope.map { it.accept(this) })

    override fun visitCode(code: BlockToken.Code): BlockToken =
        code.copy(scope = code.definition.accept(ScopeTransformer(code, this)))

    override fun visitContent(content: BlockToken.Content): BlockToken =
        content.copy(leaf = content.leaf.copyWithReplacements())

    override fun visitComment(comment: BlockToken.Comment): BlockToken {
        if (comment.opener != null && comment.closer != null)
            return comment.copy(body = comment.body.copyWithReplacements())

        val body = parameters.comment(comment.body.text, false).applyReplacements()
        val token = factory.create(NativeStitcherType.COMMENT_BODY, comment.start, comment.end, body)
        return BlockToken.Comment(null, token, comment.closer?.takeIf { it.text.hasLineBreak() })
    }

    internal fun copy(): BlockTransformer = BlockTransformer(factory, state)

    private fun LeafToken.copyWithReplacements(): LeafToken =
        copy(text = text.applyReplacements())
}

private class ScopeTransformer(val code: BlockToken.Code, val context: BlockTransformer) :
    DefinitionToken.Visitor<List<BlockToken>>, TransformerStateV2 by context {
    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal): List<BlockToken> {
        if (!checker.check(swap)) return code.scope

        val variant = swap.entries.first { eval(it.expression) }
        return wrapSwapReplacement(variant.literal.unquote())
    }

    override fun visitSwapOpener(swap: DefinitionToken.SwapOpener): List<BlockToken> {
        if (!checker.check(swap)) return code.scope

        val replacement = processTemplate(parameters.swaps[swap.id]!!, swap.arguments)
        return wrapSwapReplacement(replacement)
    }

    override fun visitSwapCloser(swap: DefinitionToken.SwapCloser): List<BlockToken> = emptyList()

    override fun visitConditionOpener(cond: DefinitionToken.ConditionOpener): List<BlockToken> {
        context.conditionCarry = false
        return if (!checker.check(cond)) code.scope
        else processCondition(cond.expression)
    }

    override fun visitConditionExtension(cond: DefinitionToken.ConditionExtension): List<BlockToken> {
        return if (!checker.check(cond)) code.scope
        else processCondition(cond.expression)
    }

    override fun visitConditionCloser(cond: DefinitionToken.ConditionCloser): List<BlockToken> {
        context.conditionCarry = false
        return emptyList()
    }

    override fun visitReplacementToggle(repl: DefinitionToken.ReplacementToggle): List<BlockToken> {
        checker.check(repl)

        val enable = mutableSetOf<String>()
        val disable = mutableSetOf<String>()
        for ((op, id) in repl.entries) when(op) {
            null -> enable += id.text
            else -> disable += id.text
        }

        replacer.update(enable, disable)
        return emptyList()
    }

    override fun visitReplacementLocal(repl: DefinitionToken.ReplacementLocal): List<BlockToken> {
        if (!checker.check(repl)) return code.scope.reprocess()

        val direction = eval(repl.expression)
        val (source, target) = if (direction) repl.source to repl.target else repl.target to repl.source
        replacer.localExecutor.push(source.unquote(), target.unquote())
        return code.scope.reprocess()
            .also { replacer.localExecutor.pop() }
    }

    override fun visitReplacementCloser(repl: DefinitionToken.ReplacementCloser): List<BlockToken> =
        emptyList()

    private fun wrapSwapReplacement(replacement: String): List<BlockToken> {
        val content = parameters.replace(code.scope.join(), replacement)
        val start = code.scope.firstOrNull()?.start ?: code.host.end
        val end = code.scope.lastOrNull()?.end ?: code.host.end

        val token = context.factory.create(NativeStitcherType.CONTENT, start, end, content).let(BlockToken::Content)
        // FIXME: Issues inside substituted values will have incorrect context
        return listOf(token).uncomment(reparse = true)
    }

    private fun processTemplate(template: String, tokens: List<LeafToken>): String {
        return if (tokens.isEmpty()) template else buildString(template) {
            var offset = 0
            val insertions = tokens.map(LeafToken::unquote)
            for (place in template.findInsertionPlaces())
                offset += place.apply(this, offset) { insertions[it - 1] }
        }
    }

    private fun processCondition(expr: ExpressionToken?): List<BlockToken> {
        val enable = eval(expr) && !context.conditionCarry
        context.conditionCarry = enable || context.conditionCarry

        val commented = code.scope.isCommented()
        return when(enable) {
            true if commented -> code.scope.uncomment(reparse = false)
            false if !commented -> code.scope.comment()
            else -> code.scope.reprocess()
        }
    }

    private fun List<BlockToken>.isCommented(): Boolean =
        all { it is BlockToken.Comment || (it is BlockToken.Content && it.isBlank()) }

    private fun List<BlockToken>.comment(): List<BlockToken> {
        if (isEmpty()) return emptyList()

        // TODO: Is reprocessing needed if the reformat task is going to be added?
        val content = parameters.comment(reprocess().join(), code.definition.opener == null)
        return listOf(context.factory.create(NativeStitcherType.CONTENT, first().start, last().end, content).let(BlockToken::Content))
    }

    private fun List<BlockToken>.uncomment(reparse: Boolean): List<BlockToken> {
        if (isEmpty()) return emptyList()

        val stream = UncommentedTokenSource(this, context, reparse).let(::CommonTokenStream)
        val factory = StitcherTokenFactory.Inline(first().start)
        return problems.runMarked({ LayoutParser.parse(stream, input, factory, problems) }) { root, ok ->
            if (ok) root.scope.reprocess() else {
                checker.check(root)
                root.scope
            }
        }
    }

    private fun List<BlockToken>.reprocess(): List<BlockToken> = if (isEmpty()) emptyList() else {
        val copy = context.copy()
        map { it.accept(copy) }
    }
}

interface TransformerStateV2 {
    val parameters: TransformParameters
    val problems: FileProblemReporter
    val input: CharStream

    val checker: ParameterChecker
    val replacer: CompositeReplacementExecutor

    fun eval(expr: ExpressionToken?): Boolean

    fun String.applyReplacements(): String
}

private class TransformerStateImpl(
    override val parameters: TransformParameters,
    override val problems: FileProblemReporter,
    override val input: CharStream,
) : TransformerStateV2 {
    val evaluator: ExpressionEvaluator = ExpressionEvaluator(parameters)
    override val checker: ParameterChecker = ParameterChecker(parameters, problems)
    override val replacer: CompositeReplacementExecutor = CompositeReplacementExecutor(parameters.replacements)

    override fun eval(expr: ExpressionToken?): Boolean = expr?.accept(evaluator) ?: true
    override fun String.applyReplacements(): String = let(replacer::replace)
}

private class UncommentedTokenSource(tokens: Iterable<BlockToken>, state: TransformerStateV2, val reparse: Boolean): ListTokenSource(mutableListOf()) {
    private val listener: StitcherErrorListener = StitcherErrorListener(state.problems)
    private val list: MutableList<AntlrToken>
        inline get() = tokens as MutableList<AntlrToken>

    init {
        val source: Pair<TokenSource?, CharStream?> = Pair(this, state.input)
        val builder = TokenListBuilder(source, state)
        for (token in tokens) token.accept(builder)
    }

    inner class TokenListBuilder(val source: Pair<TokenSource?, CharStream?>, state: TransformerStateV2) : BlockToken.Visitor<Unit>, TransformerStateV2 by state {
        override fun visitContent(content: BlockToken.Content) {
            if (reparse) acceptReparsed(content.leaf.text, problems.locate(content))
            else list += tokenFactory.create(source, content.leaf, problems)
        }

        override fun visitComment(comment: BlockToken.Comment) {
            val location = problems.locate(comment.body)
            val content = parameters.uncomment(comment.body.text, comment.openerStr, comment.closerStr)
            acceptReparsed(content, location)
        }

        override fun visitCode(code: BlockToken.Code) {
            throw UnsupportedOperationException()
        }

        override fun visitRoot(root: BlockToken.Root) {
            throw UnsupportedOperationException()
        }

        private fun acceptReparsed(text: String, location: ProblemLocation) {
            assert(location.fileIndex > 0) { "Incomplete location $location" }
            val input = text.charStream()
            val scanner = parameters.create(input, problems).apply {
                scanner.errorListener = listener
                scanner.tokenFactory = InlineTokenFactory(location.lineIndex, location.charOffset)
            }

            scanner.forEach(list::plusAssign)
        }
    }
}
