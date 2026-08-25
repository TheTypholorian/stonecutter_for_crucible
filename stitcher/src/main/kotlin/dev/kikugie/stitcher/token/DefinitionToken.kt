package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.token.type.BuiltinTokenType

sealed interface DefinitionToken : StitcherToken {
    val kind: Kind
    val body: Body
    val role: Role

    val closer: LeafToken? get() = null
    val opener: ScopeToken? get() = null

    fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T

    enum class Kind { CONDITION, SWAP, REPLACEMENT }

    enum class Body { SCOPED, LINE, LOOKUP, NONE }

    enum class Role { OPENER, EXTENSION, CLOSER, INDEPENDENT }

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

    sealed interface Swap : DefinitionToken {
        override val kind: Kind get() = Kind.SWAP
    }

    data class SwapLocal(val entries: List<Entry>, override val opener: ScopeToken?) : Swap {
        override val type: BuiltinTokenType get() = BuiltinTokenType.SWAP_LOCAL
        override val body: Body = body()
        override val role: Role get() = Role.OPENER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitSwapLocal(this, ctx)

        data class Entry(val sugar: List<LeafToken>, val expression: ExpressionToken?, val literal: LeafToken) : StitcherToken {
            override val type: BuiltinTokenType get() = BuiltinTokenType.SWAP_EXPR

            fun <T, C> accept(visitor: StitcherToken.Visitor<Entry, T, C>, ctx: C): T = visitor.visitToken(this, ctx)
        }
    }

    data class SwapOpener(val identifier: LeafToken, val arguments: List<LeafToken>, override val opener: ScopeToken?) : Swap {
        override val type: BuiltinTokenType get() = BuiltinTokenType.SWAP_OPEN
        override val body: Body = body()
        override val role: Role get() = Role.OPENER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitSwapOpener(this, ctx)
    }

    data class SwapCloser(override val closer: LeafToken) : Swap, StitcherToken by closer {
        override val type: BuiltinTokenType get() = BuiltinTokenType.SWAP_CLOSE
        override val body: Body get() = Body.NONE
        override val role: Role get() = Role.CLOSER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitSwapCloser(this, ctx)
    }

    sealed interface Condition : DefinitionToken {
        override val kind: Kind get() = Kind.CONDITION
        val sugar: List<LeafToken> get() = emptyList()
        val expression: ExpressionToken? get() = null
    }

    data class ConditionOpener(
        override val sugar: List<LeafToken>,
        override val expression: ExpressionToken, override val opener: ScopeToken?
    ) : Condition {
        override val type: BuiltinTokenType get() = BuiltinTokenType.COND_OPEN
        override val body: Body = body()
        override val role: Role get() = Role.OPENER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitConditionOpener(this, ctx)
    }

    data class ConditionExtension(
        override val closer: LeafToken?, override val sugar: List<LeafToken>,
        override val expression: ExpressionToken?, override val opener: ScopeToken?
    ) : Condition {
        override val type: BuiltinTokenType get() = BuiltinTokenType.COND_EXT
        override val body: Body = body()
        override val role: Role get() = Role.EXTENSION

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitConditionExtension(this, ctx)
    }

    data class ConditionCloser(override val closer: LeafToken) : Condition, StitcherToken by closer {
        override val type: BuiltinTokenType get() = BuiltinTokenType.COND_CLOSE
        override val body: Body get() = Body.NONE
        override val role: Role get() = Role.CLOSER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitConditionCloser(this, ctx)
    }

    sealed interface Replacement : DefinitionToken {
        override val kind: Kind get() = Kind.REPLACEMENT
    }

    data class ReplacementToggle(val entries: List<Entry>) : Replacement {
        override val type: BuiltinTokenType get() = BuiltinTokenType.REPL_TOGGLE
        override val body: Body get() = Body.NONE
        override val role: Role get() = Role.INDEPENDENT

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
        override val body: Body = body()
        override val role: Role = if (opener is ScopeToken.Named) Role.INDEPENDENT else Role.OPENER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitReplacementLocal(this, ctx)

        data class Entry(val source: LeafToken, val dir: LeafToken, val target: LeafToken, val comma: LeafToken?) : StitcherToken {
            override val type: BuiltinTokenType get() = BuiltinTokenType.REPL_LOCAL_ENTRY

            fun <T, C> accept(visitor: StitcherToken.Visitor<Entry, T, C>, ctx: C): T = visitor.visitToken(this, ctx)
        }
    }

    data class ReplacementCloser(override val closer: LeafToken) : Replacement, StitcherToken by closer {
        override val type: BuiltinTokenType get() = BuiltinTokenType.REPL_CLOSE
        override val body: Body get() = Body.NONE
        override val role: Role get() = Role.CLOSER

        override fun <T, C> accept(visitor: Visitor<out T, C>, ctx: C): T = visitor.visitReplacementCloser(this, ctx)
    }
}

private fun DefinitionToken.body(): DefinitionToken.Body = when (opener?.type as? BuiltinTokenType) {
    BuiltinTokenType.CLOSED_SCOPE -> DefinitionToken.Body.SCOPED
    BuiltinTokenType.LOOKUP_SCOPE -> DefinitionToken.Body.LOOKUP
    BuiltinTokenType.NAMED_SCOPE -> DefinitionToken.Body.NONE
    else -> DefinitionToken.Body.LINE
}
