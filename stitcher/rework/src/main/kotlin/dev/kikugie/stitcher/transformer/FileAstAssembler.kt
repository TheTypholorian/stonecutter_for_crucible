package dev.kikugie.stitcher.transformer

import dev.kikugie.antlr.util.toSequence
import dev.kikugie.stitcher.StitcherParameters
import dev.kikugie.stitcher.document.InjectedDocument
import dev.kikugie.stitcher.document.edit.ModificationTracker
import dev.kikugie.stitcher.document.join
import dev.kikugie.stitcher.document.reference
import dev.kikugie.stitcher.replacement.CompositeExecutor
import dev.kikugie.stitcher.token.*
import dev.kikugie.stitcher.token.BlockToken.*
import dev.kikugie.stitcher.token.DefinitionToken.*
import dev.kikugie.stitcher.util.append
import dev.kikugie.stitcher.util.findInsertionPlaces
import dev.kikugie.stitcher.util.indexOfFirst
import dev.kikugie.stitcher.util.tokenize
import dev.kikugie.stonecutter.model.process.StringReplacement

class FileAstAssembler(val params: StitcherParameters) : BlockToken.Visitor<BlockToken, Nothing?>, DefinitionToken.Visitor<Code, Code> {
    private val replacer: CompositeExecutor = CompositeExecutor(params.vars.replacements)

    override fun visitContent(content: Content, ctx: Nothing?): BlockToken = content
    override fun visitComment(comment: Comment, ctx: Nothing?): BlockToken = comment
    override fun visitCode(code: Code, ctx: Nothing?): BlockToken = code.definition.accept(this, code)
    override fun visitRoot(root: Root, ctx: Nothing?): BlockToken = root.copy(scope = assembleScope(root.scope))

    override fun visitSwapLocal(swap: SwapLocal, ctx: Code): Code = substituteSwap(ctx, resolveLocalSwap(swap))
    override fun visitSwapOpener(swap: SwapOpener, ctx: Code): Code = substituteSwap(ctx, resolveTemplateSwap(swap))
    override fun visitSwapCloser(swap: SwapCloser, ctx: Code): Code = ctx
    override fun visitConditionOpener(cond: ConditionOpener, ctx: Code): Code = assembleBranch(ctx, eval(cond.expression))
    override fun visitConditionExtension(cond: ConditionExtension, ctx: Code): Code = assembleBranch(ctx, eval(cond.expression))
    override fun visitConditionCloser(cond: ConditionCloser, ctx: Code): Code = ctx
    override fun visitReplacementToggle(repl: ReplacementToggle, ctx: Code): Code = ctx.also { applyReplacementToggle(repl) }
    override fun visitReplacementLocal(repl: ReplacementLocal, ctx: Code): Code = applyReplacementLocal(ctx, repl)
    override fun visitReplacementCloser(repl: ReplacementCloser, ctx: Code): Code = ctx

    private fun eval(expression: ExpressionToken?): Boolean = expression?.accept(ExpressionEvaluator, params.vars) ?: true

    private fun assembleScope(scope: List<BlockToken>): List<BlockToken> = ArrayList<BlockToken>().apply {
        var carry = false
        var index = 0
        while (index in scope.indices) when (val node = scope[index]) {
            is Content, is Comment -> index += assembleContent(scope, index)
            is Code -> this += when (val def = node.definition.also { index++ }) {
                is ConditionOpener -> assembleBranch(node, eval(def.expression).also { carry = it })
                is ConditionExtension -> assembleBranch(node, (!carry && eval(def.expression)).also { carry = carry || it })
                is ConditionCloser -> node.also { carry = false }
                else -> node.accept(this@FileAstAssembler, null)
            }
            else -> index++
        }
    }

    // Apply replacements to content and join it as a single content block
    context(collector: MutableList<BlockToken>)
    private fun assembleContent(scope: List<BlockToken>, start: Int): Int {
        // Inside the tree, anything other than [Content] or [Comment] can only be [Code].
        val end: Int = scope.indexOfFirst(start) { it is Code }.let { if (it == -1) scope.size else it }
        if (replacer.isEmpty()) return collector.append(scope, start, end)

        val tracker = ModificationTracker(scope[start].ref join scope[end - 1].ref)
        if (!replacer.replace(tracker)) return collector.append(scope, start, end)

        val document = InjectedDocument(tracker)
        collector += Content(document.reference())
        return end - start
    }

    // Reprocess [code] and comment it if necessary
    private fun assembleBranch(code: Code, keepEnabled: Boolean): Code {
        val body: List<BlockToken> = code.scope.ifEmpty { return code }
        val scope: List<BlockToken> = assembleScope(body)
        if (keepEnabled) return code.copy(scope = scope)

        val spliced = ModificationTracker(body.first().ref join body.last().ref).run {
            replace(start, end, buildString { scope.appendTo(this) })
            InjectedDocument(this)
        }
        val commented = ModificationTracker(spliced, 0, spliced.length).run {
            params.commenter.comment(this, tokenize(spliced, params.scanner).toSequence().map { it.toLeaf(document) })
            InjectedDocument(this)
        }
        return code.copy(scope = listOf(Content(commented.reference())))
    }

    private fun substituteSwap(code: Code, value: String): Code {
        val substituted = ModificationTracker((code.scope.firstOrNull() ?: code).ref join (code.scope.lastOrNull() ?: code).ref).run {
            params.swapper.substitute(this, value)
            InjectedDocument(this)
        }
        // Swap contents should not be reprocessed
        return code.copy(scope = listOf(Content(substituted.reference())))
    }

    private fun applyReplacementToggle(def: ReplacementToggle) {
        val enable = HashSet<String>()
        val disable = HashSet<String>()
        for (entry in def.entries) when (entry.op) {
            null -> enable += entry.id
            else -> disable += entry.id
        }

        replacer.toggle(enable, disable)
    }

    private fun applyReplacementLocal(code: Code, def: ReplacementLocal): Code {
        val direction: Boolean = eval(def.expression)
        val opener: ScopeToken.Named? = def.opener as? ScopeToken.Named

        val replacements = def.entries.map {
            val from: LeafToken = if (direction) it.source else it.target
            val to: LeafToken  = if (direction) it.target else it.source
            StringReplacement(from.unquote(), to.unquote(), opener?.id ?: it.id)
        }
        val ids = replacements.mapNotNull(StringReplacement::identifier).toSet()
        replacer += replacements.asSequence()
        replacer.toggle(enable = ids)

        return if (opener != null) code
        else code.copy(scope = assembleScope(code.scope))
            .also { replacer.toggle(disable = ids) }
    }

    private fun resolveLocalSwap(def: SwapLocal): String =
        def.entries.first { eval(it.expression) }.literal.unquote()

    private fun resolveTemplateSwap(def: SwapOpener): String =
        processTemplate(params.vars.swaps.getValue(def.id), def.arguments)

    private fun processTemplate(template: String, arguments: List<LeafToken>): String {
        if (arguments.isEmpty()) return template
        val args = arguments.map(LeafToken::unquote)
        return buildString {
            append(template)
            var offset = 0
            for (place in template.findInsertionPlaces()) {
                val replacement = args[place.number - 1]
                replace(place.start + offset, place.end + offset, replacement)
                offset += replacement.length - (place.end - place.start)
            }
        }
    }
}
