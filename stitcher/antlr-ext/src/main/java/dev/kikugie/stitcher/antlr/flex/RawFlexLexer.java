package dev.kikugie.stitcher.antlr.flex;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public interface RawFlexLexer<T> {
    void yybegin(int state);
    int yystate();
    default int yyline() { return -1; }
    default int yycolumn() { return -1; }
    int getTokenStart();
    int getTokenEnd();
    @Nullable T advance() throws IOException;
    void reset(CharSequence buf, int start, int end, int initialState);
}
