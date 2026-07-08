package dev.kikugie.stitcher.transformer.impl;

import dev.kikugie.stitcher.antlr.flex.RawFlexLexer;
%%

%class SuperscriptLexer
%implements RawFlexLexer<SuperscriptMarker>
%function advance
%type SuperscriptMarker
%unicode

SUPERSCRIPT = [⁰¹²³⁴⁵⁶⁷⁸⁹]
%%

<YYINITIAL> {
  "/\*"|"\*/" { return SuperscriptMarker.INSTANCE; }
  "/\^"|"\^/" { return SuperscriptMarker.INSTANCE; }
  ("/\^"{SUPERSCRIPT}+)|({SUPERSCRIPT}+"\^/") { return SuperscriptMarker.INSTANCE; }
  [^] { /* skip */ }
}