package dev.kikugie.stitcher.replacement

import dev.kikugie.stitcher.antlr.flex.FlexLexerAdapter
import dev.kikugie.stitcher.antlr.flex.toSequence

private val TEMPLATE_FACTORY: FlexLexerAdapter.Factory<TemplateToken> = FlexLexerAdapter.Factory { TemplateLexer(null) }

fun String.findInsertionPlaces(): Sequence<InsertionPlace> = TEMPLATE_FACTORY.create(this).toSequence().mapNotNull {
    val number = substring(it.tokenStart + 1, it.tokenEnd).toIntOrNull() ?: return@mapNotNull null
    InsertionPlace(number, it.tokenStart, it.tokenEnd)
}

internal data object TemplateToken

data class InsertionPlace(val number: Int, val start: Int, val end: Int) {
    inline fun apply(builder: StringBuilder, offset: Int, provider: (Int) -> String?): Int {
        val insertion = provider(number) ?: return 0
        builder.replace(start + offset, end + offset, insertion)
        return insertion.length - (end - start)
    }
}
