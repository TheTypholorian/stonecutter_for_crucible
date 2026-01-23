package dev.kikugie.stitcher.util.lines;

import dev.kikugie.stitcher.antlr.flex.RawFlexLexer;

%%

%class LineLexer
%implements RawFlexLexer<LineToken>
%function advance
%type LineToken

NL = \r|\n|\r\n
%%

<YYINITIAL> {
  {NL} { return LineToken.INSTANCE; }
  [^] { /* skip */ }
}
