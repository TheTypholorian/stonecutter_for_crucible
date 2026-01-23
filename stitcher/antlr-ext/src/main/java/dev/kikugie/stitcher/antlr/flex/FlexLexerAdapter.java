package dev.kikugie.stitcher.antlr.flex;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class FlexLexerAdapter<T> {
    private final RawFlexLexer<T> flex;
    private boolean isDone = false;
    private @Nullable T tokenMarker = null;

    public FlexLexerAdapter(RawFlexLexer<T> flex, CharSequence text) {
        this.flex = flex;
        this.flex.reset(text, 0, text.length(), 0);
    }

    public @Nullable T getTokenType() {
        updateToken();
        return tokenMarker;
    }

    public int getTokenStart() {
        updateToken();
        return flex.getTokenStart();
    }

    public int getTokenEnd() {
        updateToken();
        return flex.getTokenEnd();
    }

    public int getTokenLine() {
        updateToken();
        return flex.yyline();
    }

    public int getTokenColumn() {
        updateToken();
        return flex.yycolumn();
    }

    public boolean isDone() {
        return isDone;
    }

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

        public Factory(Supplier<RawFlexLexer<T>> supplier) {
            this.supplier = supplier;
        }

        public FlexLexerAdapter<T> create(CharSequence text) {
            return new FlexLexerAdapter<>(supplier.get(), text);
        }
    }
}
