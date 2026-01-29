package dev.kikugie.stitcher.antlr.flex;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Stateful adapter for {@link RawFlexLexer} instances.
 * JFlex lexers are used in this project for miscellaneous text scanning because they avoid token object allocations.
 */
@SuppressWarnings("unused")
public class FlexLexerAdapter<T> {
    private final RawFlexLexer<T> flex;
    private boolean isDone = false;
    private @Nullable T tokenMarker = null;

    /** Creates an adapter and initializes the lexer for the given text. */
    public FlexLexerAdapter(RawFlexLexer<T> flex, CharSequence text) {
        this.flex = flex;
        this.flex.reset(text, 0, text.length(), 0);
    }

    /** Returns the current token type or {@code null} when the lexer is exhausted. */
    public @Nullable T getTokenType() {
        updateToken();
        return tokenMarker;
    }

    /** Returns the inclusive start index of the current token. */
    public int getTokenStart() {
        updateToken();
        return flex.getTokenStart();
    }

    /** Returns the exclusive end index of the current token. */
    public int getTokenEnd() {
        updateToken();
        return flex.getTokenEnd();
    }

    /** Returns the zero-based line index for the current token. */
    public int getTokenLine() {
        updateToken();
        return flex.yyline();
    }

    /** Returns the zero-based column index for the current token. */
    public int getTokenColumn() {
        updateToken();
        return flex.yycolumn();
    }

    /** True when the lexer has no more tokens to emit. */
    public boolean isDone() {
        return isDone;
    }

    /** Advances to the next token. */
    public void advance() {
        if (isDone) throw new NoSuchElementException("The lexer has no more tokens");
        updateToken();

        if (tokenMarker == null) isDone = true;
        tokenMarker = null;
    }

    private void updateToken() {
        if (tokenMarker == null) try {
            this.tokenMarker = flex.advance();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Factory<T> {
        private final Supplier<RawFlexLexer<T>> supplier;

        /** Creates a factory that supplies fresh lexer instances. */
        public Factory(Supplier<RawFlexLexer<T>> supplier) {
            this.supplier = supplier;
        }

        /** Creates a new adapter for the supplied lexer and input text. */
        public FlexLexerAdapter<T> create(CharSequence text) {
            return new FlexLexerAdapter<>(supplier.get(), text);
        }
    }
}
