package dev.kikugie.stitcher.transformer

import dev.kikugie.commons.takeAsOrNull
import dev.kikugie.commons.then
import dev.kikugie.stitcher.antlr.*
import dev.kikugie.stitcher.issue.FileProblemReporter
import dev.kikugie.stitcher.issue.ProblemLocation
import dev.kikugie.stitcher.issue.locate
import dev.kikugie.stitcher.issue.runMarked
import dev.kikugie.stitcher.parser.FileAstParser
import dev.kikugie.stitcher.parser.StitcherTokenFactory
import dev.kikugie.stitcher.replacement.ReplacementExecutor
import dev.kikugie.stitcher.replacement.StringReplacement
import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.eval.join
import dev.kikugie.stitcher.token.impl.BlockToken
import dev.kikugie.stitcher.token.impl.DefinitionToken
import dev.kikugie.stitcher.token.impl.ExpressionToken
import dev.kikugie.stitcher.token.impl.LeafToken
import dev.kikugie.stitcher.token.impl.ScopeToken
import dev.kikugie.stitcher.transformer.visitor.ExpressionEvaluator
import dev.kikugie.stitcher.transformer.visitor.ParameterChecker
import dev.kikugie.stitcher.util.*
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ListTokenSource
import org.antlr.v4.runtime.TokenSource
import org.antlr.v4.runtime.misc.Pair
import java.util.UUID

private typealias BlockTokenList = List<BlockToken>

class BlockTransformer private constructor(val factory: StitcherTokenFactory, private val state: TransformerStateImpl) :
    BlockToken.Visitor<BlockToken>, TransformerState by state {
    constructor(
        parameters: TransformParameters,
        problems: FileProblemReporter,
        input: CharStream,
        factory: StitcherTokenFactory = StitcherTokenFactory,
    ) : this(factory, TransformerStateImpl(parameters, problems, input))

    internal var conditionCarry: Boolean = false
    private val blockCollector: BlockCollector = BlockCollector(replacer)

    override fun visitRoot(root: BlockToken.Root): BlockToken {
        for (it in root.scope)
            it.accept(this).accept(blockCollector)
        blockCollector.submit()
        return BlockToken.Root(blockCollector.list)
    }

    override fun visitCode(code: BlockToken.Code): BlockToken {
        blockCollector.submit()
        val result = code.definition.accept(ScopeTransformer(code, this))
        return code.copy(scope = result)
    }

    override fun visitContent(content: BlockToken.Content): BlockToken =
        content

    override fun visitComment(comment: BlockToken.Comment): BlockToken {
        if (comment.opener != null && comment.closer != null) return comment

        val body = parameters.comment(comment.body.text, false)
        val token = factory.create(NativeStitcherType.COMMENT_BODY, comment.start, comment.end, body)
        return BlockToken.Comment(null, token, comment.closer?.takeIf { it.text.hasLineBreak() })
    }

    internal fun copy(): BlockTransformer = BlockTransformer(factory, state)
}

private class ScopeTransformer(val code: BlockToken.Code, val context: BlockTransformer) :
    DefinitionToken.Visitor<BlockTokenList>, TransformerState by context {
    override fun visitSwapLocal(swap: DefinitionToken.SwapLocal): BlockTokenList {
        if (!checker.check(swap)) return code.scope

        val variant = swap.entries.first { eval(it.expression) }
        return wrapSwapReplacement(variant.literal.unquote())
    }

    override fun visitSwapOpener(swap: DefinitionToken.SwapOpener): BlockTokenList {
        if (!checker.check(swap)) return code.scope

        val replacement = processTemplate(parameters.swaps[swap.id]!!, swap.arguments)
        return wrapSwapReplacement(replacement)
    }

    override fun visitSwapCloser(swap: DefinitionToken.SwapCloser): BlockTokenList =
        emptyList()

    override fun visitConditionOpener(cond: DefinitionToken.ConditionOpener): BlockTokenList {
        context.conditionCarry = false
        return if (!checker.check(cond)) code.scope
        else processCondition(cond.expression)
    }

    override fun visitConditionExtension(cond: DefinitionToken.ConditionExtension): BlockTokenList {
        return if (!checker.check(cond)) code.scope
        else processCondition(cond.expression)
    }

    override fun visitConditionCloser(cond: DefinitionToken.ConditionCloser): BlockTokenList =
        emptyList<BlockToken>().also { context.conditionCarry = false }

    override fun visitReplacementToggle(repl: DefinitionToken.ReplacementToggle): BlockTokenList {
        checker.check(repl)

        val enable = mutableSetOf<String>()
        val disable = mutableSetOf<String>()
        for ((op, id) in repl.entries) when (op) {
            null -> enable += id.text
            else -> disable += id.text
        }

        replacer.toggle(enable, disable)
        return emptyList()
    }

    override fun visitReplacementLocal(repl: DefinitionToken.ReplacementLocal): BlockTokenList {
        if (!checker.check(repl)) return code.scope.reprocess()

        val direction = eval(repl.expression)
        val identifier = repl.opener?.takeAsOrNull<ScopeToken.Named>()?.identifier?.text
            ?: UUID.randomUUID().toString()

        val replacements = repl.entries.map {
            val from = if (direction) it.source else it.target
            val to = if (direction) it.target else it.source
            StringReplacement(to.unquote(), from.unquote(), identifier)
        }

        val idSet = setOf(identifier)
        replacer.toggle(enable = idSet)
        replacer.include(replacements)
        return if (repl.opener is ScopeToken.Named) emptyList() else code.scope.reprocess().also {
            replacer.toggle(disable = idSet)
        }
    }

    override fun visitReplacementCloser(repl: DefinitionToken.ReplacementCloser): BlockTokenList =
        emptyList()

    private fun wrapSwapReplacement(replacement: String): BlockTokenList {
        val content = parameters.replace(code.scope.join(), replacement)
        val start = code.scope.firstOrNull()?.start ?: code.host.end
        val end = code.scope.lastOrNull()?.end ?: code.host.end

        val token = context.factory.create(NativeStitcherType.CONTENT, start, end, content).let(BlockToken::Content)
        // FIXME: Issues inside substituted values will have incorrect context
        return listOf(token).uncomment(reparse = true).reprocess()
    }

    private fun processTemplate(template: String, tokens: List<LeafToken>): String {
        return if (tokens.isEmpty()) template else buildString(template) {
            var offset = 0
            val insertions = tokens.map(LeafToken::unquote)
            for (place in template.findInsertionPlaces())
                offset += place.apply(this, offset) { insertions[it - 1] }
        }
    }

    private fun processCondition(expr: ExpressionToken?): BlockTokenList {
        val enable = !context.conditionCarry && eval(expr)
        context.conditionCarry = enable || context.conditionCarry

        val commented = code.scope.isCommented()
        return when (enable) {
            true if commented -> code.scope.uncomment(reparse = false)
            false if !commented -> code.scope.comment()
            else -> code.scope.reprocess()
        }
    }

    private fun List<BlockToken>.isCommented(): Boolean =
        all { it is BlockToken.Comment || (it is BlockToken.Content && it.isBlank()) }

    private fun List<BlockToken>.comment(): BlockTokenList {
        if (isEmpty()) return emptyList()

        val content = parameters.comment(reprocess().join(), code.definition.opener == null)
        val block = BlockToken.Content(context.factory.create(NativeStitcherType.CONTENT, first().start, last().end, content))
        return listOf(block)
    }

    private fun List<BlockToken>.uncomment(reparse: Boolean): BlockTokenList {
        if (isEmpty()) return emptyList()

        val stream = UncommentedTokenSource(this, context, reparse).let(::CommonTokenStream)
        val factory = StitcherTokenFactory.Inline(first().start)
        return problems.runMarked({ FileAstParser.parse(stream, input, factory, problems) }) { root, ok ->
            if (ok) root.scope.reprocess() else checker.check(root) then root.scope
        }
    }

    private fun List<BlockToken>.reprocess(): BlockTokenList {
        if (isEmpty()) return emptyList()
        val block = BlockToken.Root(this).accept(context.copy()) as BlockToken.Root
        return block.scope
    }
}

private interface TransformerState {
    val parameters: TransformParameters
    val problems: FileProblemReporter
    val input: CharStream

    val checker: ParameterChecker
    val replacer: ReplacementExecutor

    fun eval(expr: ExpressionToken?): Boolean
}

private class TransformerStateImpl(
    override val parameters: TransformParameters,
    override val problems: FileProblemReporter,
    override val input: CharStream,
) : TransformerState {
    val evaluator: ExpressionEvaluator = ExpressionEvaluator(parameters)
    override val checker: ParameterChecker = ParameterChecker(parameters, problems)
    override val replacer: ReplacementExecutor = ReplacementExecutor(parameters.replacements, problems)

    override fun eval(expr: ExpressionToken?): Boolean = expr?.accept(evaluator) ?: true
}

private class UncommentedTokenSource(tokens: Iterable<BlockToken>, state: TransformerState, val reparse: Boolean) :
    ListTokenSource(mutableListOf()) {
    private val listener: StitcherErrorListener = StitcherErrorListener(state.problems)
    private val list: MutableList<AntlrToken>
        inline get() = tokens as MutableList<AntlrToken>

    init {
        val source: Pair<TokenSource?, CharStream?> = Pair(this, state.input)
        val builder = TokenListBuilder(source, state)
        for (token in tokens) token.accept(builder)
    }

    inner class TokenListBuilder(val source: Pair<TokenSource?, CharStream?>, state: TransformerState) :
        BlockToken.Visitor<Unit>, TransformerState by state {
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
