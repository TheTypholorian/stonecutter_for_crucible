package dev.kikugie.stitcher.replacement;
%%

%class TemplateLexer
%implements RawFlexLexer<TemplateToken>
%function advance
%type TemplateToken

ID = [1-9][0-9]*
%%

<YYINITIAL> {
  "$"{ID} { return TemplateToken.INSTANCE; }
  [^] { /* skip */ }
}
