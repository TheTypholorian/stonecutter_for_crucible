lexer grammar StarCommentEscaper;

fragment SUPERSCRIPT: [⁰¹²³⁴⁵⁶⁷⁸⁹];
fragment DOUBLE_BACKSLASH: '\\\\';

TILDA_COMMENT_START: '/^'(SUPERSCRIPT*) -> pushMode(IN_TILDA);
SLASH_COMMENT_START: '//' -> pushMode(IN_SLASH), skip;

SINGLE_QUOTE: '\'' -> pushMode(IN_CHAR), skip;
DOUBLE_QUOTE: '"' -> pushMode(IN_STRING), skip;
BLOCK_QUOTE: '"""' -> pushMode(IN_BLOCK), skip;

mode IN_TILDA;
TILDA_COMMENT_NEST: '/^'(SUPERSCRIPT*) -> pushMode(IN_TILDA), type(TILDA_COMMENT_START);
TILDA_COMMENT_END: (SUPERSCRIPT*)'^/' -> popMode;

SINGLE_QUOTE_NEST: '\'' -> pushMode(IN_CHAR), skip;
DOUBLE_QUOTE_NEST: '"' -> pushMode(IN_STRING), skip;
BLOCK_QUOTE_NEST: '"""' -> pushMode(IN_BLOCK), skip;

mode IN_SLASH;
SLASH_COMMENT_END: '\n' -> popMode, skip;
SLASH_THE_REST: (~'\n')+ -> skip;

mode IN_CHAR;
CHAR_END: '\'' -> popMode, skip;
CHAR_ESCAPE: (~'\'' | '\\\'' | DOUBLE_BACKSLASH) -> skip;

mode IN_STRING;
STR_END: '"' -> popMode, skip;
STR_ESC_END: (~'"' | '\\"' | DOUBLE_BACKSLASH) -> skip;

mode IN_BLOCK;
BLOCK_END: '"""' -> popMode, skip;
BLOCK_ESC_END: (. | '\\"' | DOUBLE_BACKSLASH) -> skip;
