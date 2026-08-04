package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.token.DefinitionToken.Kind
import dev.kikugie.stitcher.token.type.BuiltinTokenType

sealed interface DefinitionToken : StitcherToken {
    val kind: Kind
    val closer: LeafToken? get() = null
    val opener: ScopeToken? get() = null

    fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T

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
    }

    sealed interface Swap : DefinitionToken

    data class SwapLocal(val entries: List<Entry>, override val opener: ScopeToken?) : Swap {
        override val type: BuiltinTokenType get() = BuiltinTokenType.SWAP_LOCAL
        override val kind: Kind = opener.openerKind()

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitSwapLocal(this, ctx)

        data class Entry(val sugar: List<LeafToken>, val expression: ExpressionToken?, val literal: LeafToken) : StitcherToken {
            override val type: BuiltinTokenType get() = BuiltinTokenType.SWAP_EXPR

            fun <T, C> accept(visitor: StitcherToken.Visitor<Entry, T, C>, ctx: C): T = visitor.visitToken(this, ctx)
        }
    }

    data class SwapOpener(val identifier: LeafToken, val arguments: List<LeafToken>, override val opener: ScopeToken?) : Swap {
        override val type: BuiltinTokenType get() = BuiltinTokenType.SWAP_OPEN
        override val kind: Kind = opener.openerKind()

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitSwapOpener(this, ctx)
    }

    data class SwapCloser(override val closer: LeafToken) : Swap, StitcherToken by closer {
        override val type: BuiltinTokenType get() = BuiltinTokenType.SWAP_CLOSE
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
        override val type: BuiltinTokenType get() = BuiltinTokenType.COND_OPEN
        override val kind: Kind = opener.openerKind()

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitConditionOpener(this, ctx)
    }

    data class ConditionExtension(
        override val closer: LeafToken?, override val sugar: List<LeafToken>,
        override val expression: ExpressionToken?, override val opener: ScopeToken?
    ) : Condition {
        override val type: BuiltinTokenType get() = BuiltinTokenType.COND_EXT
        override val kind: Kind = opener.extensionKind()

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitConditionExtension(this, ctx)
    }

    data class ConditionCloser(override val closer: LeafToken) : Condition, StitcherToken by closer {
        override val type: BuiltinTokenType get() = BuiltinTokenType.COND_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitConditionCloser(this, ctx)
    }

    sealed interface Replacement : DefinitionToken

    data class ReplacementToggle(val entries: List<Entry>) : Replacement {
        override val type: BuiltinTokenType get() = BuiltinTokenType.REPL_TOGGLE
        override val kind: Kind get() = Kind.INDEPENDENT

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitReplacementToggle(this, ctx)

        data class Entry(val op: LeafToken?, val identifier: LeafToken, val comma: LeafToken?) : StitcherToken {
            override val type: BuiltinTokenType get() = BuiltinTokenType.REPL_TOGGLE_ENTRY

            fun <T, C> accept(visitor: StitcherToken.Visitor<Entry, T, C>, ctx: C): T = visitor.visitToken(this, ctx)
        }
    }

    data class ReplacementLocal(
        val sugar: LeafToken, val expression: ExpressionToken,
        val entries: List<Entry>, override val opener: ScopeToken?
    ) : Replacement {
        override val type: BuiltinTokenType get() = BuiltinTokenType.REPL_LOCAL
        override val kind: Kind = if (opener is ScopeToken.Named) Kind.INDEPENDENT else opener.openerKind()

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitReplacementLocal(this, ctx)

        data class Entry(val source: LeafToken, val dir: LeafToken, val target: LeafToken, val comma: LeafToken?) : StitcherToken {
            override val type: BuiltinTokenType get() = BuiltinTokenType.REPL_LOCAL_ENTRY

            fun <T, C> accept(visitor: StitcherToken.Visitor<Entry, T, C>, ctx: C): T = visitor.visitToken(this, ctx)
        }
    }

    data class ReplacementCloser(override val closer: LeafToken) : Replacement, StitcherToken by closer {
        override val type: BuiltinTokenType get() = BuiltinTokenType.REPL_CLOSE
        override val kind: Kind get() = Kind.CLOSER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitReplacementCloser(this, ctx)
    }
}

private fun ScopeToken?.openerKind(): Kind = when(this?.type as? BuiltinTokenType) {
    BuiltinTokenType.CLOSED_SCOPE -> Kind.SCOPED_OPENER
    BuiltinTokenType.LOOKUP_SCOPE -> Kind.LOOKUP_OPENER
    else -> Kind.LINE_OPENER
}

private fun ScopeToken?.extensionKind(): Kind = when (this?.type as? BuiltinTokenType) {
    BuiltinTokenType.CLOSED_SCOPE -> Kind.SCOPED_EXTENSION
    BuiltinTokenType.LOOKUP_SCOPE -> Kind.LOOKUP_EXTENSION
    else -> Kind.LINE_EXTENSION
}
