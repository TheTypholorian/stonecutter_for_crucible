package dev.kikugie.stitcher.antlr.flex

inline fun <T> FlexLexerAdapter<T>.forEach(action: (state: FlexLexerAdapter<T>) -> Unit) {
    while (tokenType != null) {
        action(this)
        advance()
    }
}

fun <T> FlexLexerAdapter<T>.toSequence(): Sequence<FlexLexerAdapter<T>> = sequence {
    forEach { yield(it) }
}
