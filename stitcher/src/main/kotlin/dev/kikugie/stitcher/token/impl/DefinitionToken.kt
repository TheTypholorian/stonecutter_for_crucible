package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.DefinitionToken.Kind
import dev.kikugie.stitcher.util.firstNotNull
import dev.kikugie.stitcher.util.lastNotNull

sealed interface DefinitionToken : StitcherToken {
    val kind: Kind
    val closer: LeafToken? get() = null
    val opener: ScopeToken? get() = null

    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visitSwapLocal(swap: SwapLocal): T
        fun visitSwapOpener(swap: SwapOpener): T
        fun visitSwapCloser(swap: SwapCloser): T

        fun visitConditionOpener(cond: ConditionOpener): T
        fun visitConditionExtension(cond: ConditionExtension): T
        fun visitConditionCloser(cond: ConditionCloser): T

        fun visitReplacementToggle(repl: ReplacementToggle): T
        fun visitReplacementLocal(repl: ReplacementLocal): T
        fun visitReplacementCloser(repl: ReplacementCloser): T
    }

    enum class Kind {
        SCOPED_OPENER, LINE_OPENER, LOOKUP_OPENER,
        SCOPED_EXTENSION, LINE_EXTENSION, LOOKUP_EXTENSION,
        CLOSER, INDEPENDENT;

        val isScoped: Boolean
            get() = when (this) {
                SCOPED_OPENER, SCOPED_EXTENSION -> true
                else -> false
            }

        val isOpen: Boolean
            get() = when (this) {
                LINE_OPENER, LOOKUP_OPENER, LINE_EXTENSION, LOOKUP_EXTENSION -> true
                else -> false
            }

        val isExtension: Boolean
            get() = when (this) {
                SCOPED_EXTENSION, LINE_EXTENSION, LOOKUP_EXTENSION, CLOSER -> true
                else -> false
            }

        val isEmpty: Boolean
            get() = when (this) {
                CLOSER, INDEPENDENT -> true
                else -> false
            }
    }

    sealed interface Swap : DefinitionToken

    data class SwapLocal(val entries: List<Entry>, override val opener: ScopeToken?) : Swap {
        override val type: StitcherType get() = NativeStitcherType.SWAP_LOCAL
        override val kind: Kind = opener.openerKind()
        override val start: Int get() = entries.first().start
        override val end: Int = lastNotNull(entries.last(), opener).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitSwapLocal(this)

        data class Entry(
            val sugar: List<LeafToken>,
            val expression: ExpressionToken?,
            val literal: LeafToken,
        ) : StitcherToken {
            override val type: StitcherType get() = NativeStitcherType.SWAP_EXPR
            override val start: Int = firstNotNull(sugar.firstOrNull(), expression, literal).start
            override val end: Int get() = literal.end
        }
    }

    data class SwapOpener(val identifier: LeafToken, val arguments: List<LeafToken>, override val opener: ScopeToken?) : Swap {
        override val type: NativeStitcherType get() = NativeStitcherType.SWAP_OPEN
        override val kind: Kind = opener.openerKind()
        override val start: Int get() = identifier.start
        override val end: Int = lastNotNull(identifier, arguments.lastOrNull(), opener).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitSwapOpener(this)
    }

    data class SwapCloser(override val closer: LeafToken) : Swap, StitcherToken by closer {
        override val type: NativeStitcherType get() = NativeStitcherType.SWAP_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitSwapCloser(this)
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

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitConditionOpener(this)
    }

    data class ConditionExtension(
        override val closer: LeafToken, override val sugar: List<LeafToken>,
        override val expression: ExpressionToken?, override val opener: ScopeToken?
    ) : Condition {
        override val type: NativeStitcherType get() = NativeStitcherType.COND_EXT
        override val kind: Kind = opener.extensionKind()
        override val start: Int get() = closer.start
        override val end: Int = lastNotNull(sugar.lastOrNull(), expression, opener).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitConditionExtension(this)
    }

    data class ConditionCloser(override val closer: LeafToken) : Condition, StitcherToken by closer {
        override val type: NativeStitcherType get() = NativeStitcherType.COND_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitConditionCloser(this)
    }

    sealed interface Replacement : DefinitionToken

    data class ReplacementToggle(val entries: List<Entry>) : Replacement {
        override val type: NativeStitcherType get() = NativeStitcherType.REPL_TOGGLE
        override val kind: Kind get() = Kind.INDEPENDENT
        override val start: Int = entries.first().start
        override val end: Int = entries.last().end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitReplacementToggle(this)

        data class Entry(val op: LeafToken?, val identifier: LeafToken, val comma: LeafToken?) : StitcherToken {
            override val type: StitcherType get() = NativeStitcherType.REPL_TOGGLE_ENTRY
            override val start: Int get() = op?.start ?: identifier.start
            override val end: Int get() = comma?.end ?: identifier.end
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

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitReplacementLocal(this)

        data class Entry(val source: LeafToken, val dir: LeafToken, val target: LeafToken, val comma: LeafToken?) : StitcherToken {
            override val type: StitcherType get() = NativeStitcherType.REPL_LOCAL_ENTRY
            override val start: Int get() = source.start
            override val end: Int get() = comma?.end ?: target.end
        }
    }

    data class ReplacementCloser(override val closer: LeafToken) : Replacement, StitcherToken by closer {
        override val type: StitcherType get() = NativeStitcherType.REPL_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitReplacementCloser(this)
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
