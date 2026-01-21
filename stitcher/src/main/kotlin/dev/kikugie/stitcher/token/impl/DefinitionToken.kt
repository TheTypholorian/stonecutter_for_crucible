package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.util.firstNotNull
import dev.kikugie.stitcher.util.lastNotNull
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType
import dev.kikugie.stitcher.token.impl.DefinitionToken.Kind

public sealed interface DefinitionToken : StitcherToken {
    public val kind: Kind
    public val closer: LeafToken? get() = null
    public val opener: ScopeToken? get() = null

    public fun <T> accept(visitor: Visitor<T>): T

    public interface Visitor<T> {
        public fun visitSwap(swap: Swap): T
        public fun visitCondition(cond: Condition): T
        public fun visitReplacement(repl: Replacement): T
    }

    public enum class Kind {
        SCOPED_OPENER, LINE_OPENER, LOOKUP_OPENER,
        SCOPED_EXTENSION, LINE_EXTENSION, LOOKUP_EXTENSION,
        CLOSER, INDEPENDENT;

        public val isScoped: Boolean
            get() = when (this) {
                SCOPED_OPENER, SCOPED_EXTENSION -> true
                else -> false
            }

        public val isOpen: Boolean
            get() = when (this) {
                LINE_OPENER, LOOKUP_OPENER, LINE_EXTENSION, LOOKUP_EXTENSION -> true
                else -> false
            }

        public val isExtension: Boolean
            get() = when (this) {
                SCOPED_EXTENSION, LINE_EXTENSION, LOOKUP_EXTENSION, CLOSER -> true
                else -> false
            }

        public val isEmpty: Boolean
            get() = when (this) {
                CLOSER, INDEPENDENT -> true
                else -> false
            }
    }

    public sealed interface Swap : DefinitionToken {
        public val identifier: LeafToken? get() = null
        public val arguments: List<LeafToken> get() = emptyList()
    }

    public data class SwapOpener(override val identifier: LeafToken, override val arguments: List<LeafToken>, override val opener: ScopeToken?) : Swap {
        override val type: StitcherType get() = StitcherType.Composite.SWAP_OPEN
        override val kind: Kind = opener.openerKind()
        override val start: Int get() = identifier.start
        override val end: Int = lastNotNull(identifier, arguments.lastOrNull(), opener).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitSwap(this)
    }

    public data class SwapCloser(override val closer: LeafToken) : Swap, StitcherToken by closer {
        override val type: StitcherType get() = StitcherType.Composite.SWAP_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitSwap(this)
    }
    
    public sealed interface Condition : DefinitionToken {
        public val sugar: List<LeafToken> get() = emptyList()
        public val expression: ExpressionToken? get() = null
    }

    public data class ConditionOpener(
        override val sugar: List<LeafToken>,
        override val expression: ExpressionToken, override val opener: ScopeToken?
    ) : Condition {
        override val type: StitcherType get() = StitcherType.Composite.COND_OPEN
        override val kind: Kind = opener.openerKind()
        override val start: Int = firstNotNull(sugar.firstOrNull(), expression).start
        override val end: Int = lastNotNull(expression, opener).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitCondition(this)
    }

    public data class ConditionExtension(
        override val closer: LeafToken, override val sugar: List<LeafToken>,
        override val expression: ExpressionToken?, override val opener: ScopeToken?
    ) : Condition {
        override val type: StitcherType get() = StitcherType.Composite.COND_EXT
        override val kind: Kind = opener.extensionKind()
        override val start: Int get() = closer.start
        override val end: Int = lastNotNull(sugar.lastOrNull(), expression, opener).end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitCondition(this)
    }

    public data class ConditionCloser(override val closer: LeafToken) : Condition, StitcherToken by closer {
        override val type: StitcherType get() = StitcherType.Composite.COND_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitCondition(this)
    }

    public data class Replacement(public val expressions: List<ExpressionToken>) : DefinitionToken {
        override val type: StitcherType get() = StitcherType.Composite.REPL
        override val kind: Kind get() = Kind.INDEPENDENT
        override val start: Int = expressions.first().start
        override val end: Int = expressions.last().end

        override fun <T> accept(visitor: Visitor<T>): T = visitor.visitReplacement(this)
    }
}

private fun ScopeToken?.openerKind(): Kind = when(this?.type as? StitcherType.Custom) {
    StitcherType.Custom.CLOSED_SCOPE -> Kind.SCOPED_OPENER
    StitcherType.Custom.LOOKUP_SCOPE -> Kind.LOOKUP_OPENER
    else -> Kind.LINE_OPENER
}

private fun ScopeToken?.extensionKind(): Kind = when (this?.type as? StitcherType.Custom) {
    StitcherType.Custom.CLOSED_SCOPE -> Kind.SCOPED_EXTENSION
    StitcherType.Custom.LOOKUP_SCOPE -> Kind.LOOKUP_EXTENSION
    else -> Kind.LINE_EXTENSION
}
