package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.DefinitionToken.Kind
import dev.kikugie.stitcher.util.firstNotNull
import dev.kikugie.stitcher.util.firstNotNullOrNull
import dev.kikugie.stitcher.util.lastNotNull

sealed interface DefinitionToken : StitcherToken {
    val kind: Kind
    val closer: LeafToken? get() = null
    val opener: ScopeToken? get() = null

    fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T

    interface Visitor<T, C> {
        fun visitSwapLocal(swap: SwapLocal, ctx: C): T
        fun visitSwapOpener(swap: SwapOpener, ctx: C): T
        fun visitSwapCloser(swap: SwapCloser, ctx: C): T

        fun visitConditionOpener(cond: ConditionOpener, ctx: C): T
        fun visitConditionExtension(cond: ConditionExtension, ctx: C): T
        fun visitConditionCloser(cond: ConditionCloser, ctx: C): T

        fun visitReplacementToggle(repl: ReplacementToggle, ctx: C): T
        fun visitReplacementLocal(repl: ReplacementLocal, ctx: C): T
        fun visitReplacementCloser(repl: ReplacementCloser, ctx: C): T
        
        fun visitSwapLocalEntry(entry: SwapLocal.Entry, ctx: C): T
        fun visitReplacementLocalEntry(entry: ReplacementLocal.Entry, ctx: C): T
        fun visitReplacementToggleEntry(entry: ReplacementToggle.Entry, ctx: C): T
    }

    enum class Kind {
        SCOPED_OPENER, LINE_OPENER, LOOKUP_OPENER,
        SCOPED_EXTENSION, LINE_EXTENSION, LOOKUP_EXTENSION,
        CLOSER, INDEPENDENT;

        /**Indicates that the definition ends with a `{`.*/
        val isScoped: Boolean
            get() = this == SCOPED_OPENER || this == SCOPED_EXTENSION

        /**Indicates that the definition ends with a `>>`.*/
        val isLookup: Boolean
            get() = this == LOOKUP_OPENER || this == LOOKUP_EXTENSION

        /**Indicates that the definition has an empty ending.*/
        val isLine: Boolean
            get() = this == LINE_OPENER || this == LINE_EXTENSION

        /**Indicates that the definition doesn't need a matching `}` closer.*/
        val isOpen: Boolean
            get() = isLine || isLookup

        /**Indicates that the definition should open a new scope without closing the current one.*/
        val isOpener: Boolean
            get() = this == SCOPED_OPENER || this == LINE_OPENER || this == LOOKUP_OPENER

        /**Indicates that the definition should close the current scope and open a new one.*/
        val isExtension: Boolean
            get() = this == SCOPED_EXTENSION || this == LINE_EXTENSION || this == LOOKUP_EXTENSION

        /**Indicates that the definition should close the current scope.*/
        val isCloser: Boolean
            get() = this == CLOSER || isExtension

        /**Indicates that the definition should not have its own scope.*/
        val isEmpty: Boolean
            get() = this == CLOSER || this == INDEPENDENT
    }

    sealed interface Swap : DefinitionToken

    data class SwapLocal(val entries: List<Entry>, override val opener: ScopeToken?) : Swap {
        override val type: StitcherType get() = NativeStitcherType.SWAP_LOCAL
        override val kind: Kind = opener.openerKind()
        override val start: Int get() = entries.first().start
        override val end: Int = lastNotNull(entries.last(), opener).end

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitSwapLocal(this, ctx)

        data class Entry(
            val sugar: List<LeafToken>,
            val expression: ExpressionToken?,
            val literal: LeafToken,
        ) : StitcherToken {
            override val type: StitcherType get() = NativeStitcherType.SWAP_EXPR
            override val start: Int = firstNotNull(sugar.firstOrNull(), expression, literal).start
            override val end: Int get() = literal.end
            fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitSwapLocalEntry(this, ctx)
        }
    }

    data class SwapOpener(val identifier: LeafToken, val arguments: List<LeafToken>, override val opener: ScopeToken?) : Swap {
        override val type: NativeStitcherType get() = NativeStitcherType.SWAP_OPEN
        override val kind: Kind = opener.openerKind()
        override val start: Int get() = identifier.start
        override val end: Int = lastNotNull(identifier, arguments.lastOrNull(), opener).end

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitSwapOpener(this, ctx)
    }

    data class SwapCloser(override val closer: LeafToken) : Swap, StitcherToken by closer {
        override val type: NativeStitcherType get() = NativeStitcherType.SWAP_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitSwapCloser(this, ctx)
    }

    sealed interface Condition : DefinitionToken {
        val sugar: List<LeafToken> get() = emptyList()
        val expression: ExpressionToken? get() = null
    }

    data class ConditionOpener(
        override val sugar: List<LeafToken>,
        override val expression: ExpressionToken, override val opener: ScopeToken?
    ) : Condition {
        override val type: NativeStitcherType get() = NativeStitcherType.COND_OPEN
        override val kind: Kind = opener.openerKind()
        override val start: Int = firstNotNull(sugar.firstOrNull(), expression).start
        override val end: Int = lastNotNull(expression, opener).end

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitConditionOpener(this, ctx)
    }

    data class ConditionExtension(
        override val closer: LeafToken?, override val sugar: List<LeafToken>,
        override val expression: ExpressionToken?, override val opener: ScopeToken?
    ) : Condition {
        override val type: NativeStitcherType get() = NativeStitcherType.COND_EXT
        override val kind: Kind = opener.extensionKind()
        override val start: Int = firstNotNullOrNull(closer, sugar.firstOrNull(), expression, opener)?.start ?: 0
        override val end: Int = lastNotNull(sugar.lastOrNull(), expression, opener).end

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitConditionExtension(this, ctx)
    }

    data class ConditionCloser(override val closer: LeafToken) : Condition, StitcherToken by closer {
        override val type: NativeStitcherType get() = NativeStitcherType.COND_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitConditionCloser(this, ctx)
    }

    sealed interface Replacement : DefinitionToken

    data class ReplacementToggle(val entries: List<Entry>) : Replacement {
        override val type: NativeStitcherType get() = NativeStitcherType.REPL_TOGGLE
        override val kind: Kind get() = Kind.INDEPENDENT
        override val start: Int = entries.first().start
        override val end: Int = entries.last().end

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitReplacementToggle(this, ctx)

        data class Entry(val op: LeafToken?, val identifier: LeafToken, val comma: LeafToken?) : StitcherToken {
            override val type: StitcherType get() = NativeStitcherType.REPL_TOGGLE_ENTRY
            override val start: Int get() = op?.start ?: identifier.start
            override val end: Int get() = comma?.end ?: identifier.end
            fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitReplacementToggleEntry(this, ctx)
        }
    }

    data class ReplacementLocal(
        val sugar: LeafToken, val expression: ExpressionToken,
        val entries: List<Entry>, override val opener: ScopeToken?
    ) : Replacement {
        override val type: StitcherType get() = NativeStitcherType.REPL_LOCAL
        override val start: Int get() = sugar.start
        override val end: Int get() = opener?.end ?: entries.last().end
        override val kind: Kind = if (opener is ScopeToken.Named) Kind.INDEPENDENT else opener.openerKind()

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitReplacementLocal(this, ctx)

        data class Entry(val source: LeafToken, val dir: LeafToken, val target: LeafToken, val comma: LeafToken?) : StitcherToken {
            override val type: StitcherType get() = NativeStitcherType.REPL_LOCAL_ENTRY
            override val start: Int get() = source.start
            override val end: Int get() = comma?.end ?: target.end
            fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitReplacementLocalEntry(this, ctx)
        }
    }

    data class ReplacementCloser(override val closer: LeafToken) : Replacement, StitcherToken by closer {
        override val type: StitcherType get() = NativeStitcherType.REPL_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitReplacementCloser(this, ctx)
    }
}

private fun ScopeToken?.openerKind(): Kind = when(this?.type as? NativeStitcherType) {
    NativeStitcherType.CLOSED_SCOPE -> Kind.SCOPED_OPENER
    NativeStitcherType.LOOKUP_SCOPE -> Kind.LOOKUP_OPENER
    else -> Kind.LINE_OPENER
}

private fun ScopeToken?.extensionKind(): Kind = when (this?.type as? NativeStitcherType) {
    NativeStitcherType.CLOSED_SCOPE -> Kind.SCOPED_EXTENSION
    NativeStitcherType.LOOKUP_SCOPE -> Kind.LOOKUP_EXTENSION
    else -> Kind.LINE_EXTENSION
}
