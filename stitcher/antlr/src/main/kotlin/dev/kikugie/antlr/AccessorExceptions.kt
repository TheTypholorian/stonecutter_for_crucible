package dev.kikugie.antlr

import org.antlr.v4.runtime.ParserRuleContext
import kotlin.reflect.KClass

public abstract class ParseTreeAccessException(public val ctx: ParserRuleContext) : RuntimeException()

public class MissingTerminalNodeException(ctx: ParserRuleContext, public val type: Int, public val ordinal: Int) :
    ParseTreeAccessException(ctx) {
    override val message: String = "Missing token of type $type at ordinal $ordinal"
}

public class MissingRuleContextException(ctx: ParserRuleContext, public val type: KClass<out ParserRuleContext>, public val ordinal: Int) :
    ParseTreeAccessException(ctx) {
    override val message: String = "Missing rule of type ${type.qualifiedName} at ordinal $ordinal"
}
