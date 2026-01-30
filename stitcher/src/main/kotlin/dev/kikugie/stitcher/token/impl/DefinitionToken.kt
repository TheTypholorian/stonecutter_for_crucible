package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.token.NativeStitcherType
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.impl.DefinitionToken.Kind
import dev.kikugie.stitcher.util.firstNotNull
import dev.kikugie.stitcher.util.lastNotNull

sealed interface DefinitionToken : StitcherToken {
    val kind: Kind
    val closer: LeafToken? get() = null
    val opener: ScopeToken? get() = null

    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visitSwap(swap: Swap): T
        fun visitCondition(cond: Condition): T
        fun visitReplacement(repl: Replacement): T
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

    sealed interface Swap : DefinitionToken {
        val identifier: LeafToken? get() = null
        val arguments: List<LeafToken> get() = emptyList()
    }

    data class SwapOpener(override val identifier: LeafToken, override val arguments: List<LeafToken>, override val opener: ScopeToken?) : Swap {
        override val type: NativeStitcherType get() = NativeStitcherType.SWAP_OPEN
        override val kind: Kind = opener.openerKind()
        override val start: Int get() = identifier.start
        override val end: Int = lastNotNull(identifier, arguments.lastOrNull(), opener).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitSwap(this)
    }

    data class SwapCloser(override val closer: LeafToken) : Swap, StitcherToken by closer {
        override val type: NativeStitcherType get() = NativeStitcherType.SWAP_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitSwap(this)
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

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitCondition(this)
    }

    data class ConditionExtension(
        override val closer: LeafToken, override val sugar: List<LeafToken>,
        override val expression: ExpressionToken?, override val opener: ScopeToken?
    ) : Condition {
        override val type: NativeStitcherType get() = NativeStitcherType.COND_EXT
        override val kind: Kind = opener.extensionKind()
        override val start: Int get() = closer.start
        override val end: Int = lastNotNull(sugar.lastOrNull(), expression, opener).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitCondition(this)
    }

    data class ConditionCloser(override val closer: LeafToken) : Condition, StitcherToken by closer {
        override val type: NativeStitcherType get() = NativeStitcherType.COND_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitCondition(this)
    }

    data class Replacement(val expressions: List<ExpressionToken>) : DefinitionToken {
        override val type: NativeStitcherType get() = NativeStitcherType.REPL
        override val kind: Kind get() = Kind.INDEPENDENT
        override val start: Int = expressions.first().start
        override val end: Int = expressions.last().end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitReplacement(this)
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
