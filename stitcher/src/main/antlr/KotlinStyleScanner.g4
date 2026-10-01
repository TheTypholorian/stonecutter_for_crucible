lexer grammar KotlinStyleScanner;

fragment LINE_BREAK: '\r'|'\n'|'\r\n';
fragment DOUBLE_SLASH: '\\\\';

SLASH_COMMENT_START: '//' -> pushMode(IN_SLASH);
STAR_COMMENT_START: '/*' -> pushMode(IN_STAR);

SINGLE_QUOTE: '\'' -> pushMode(IN_CHAR), skip;
DOUBLE_QUOTE: '"' -> pushMode(IN_STRING), skip;
BLOCK_QUOTE: '"""' -> pushMode(IN_BLOCK), skip;

THE_REST: . -> skip;

mode IN_SLASH;
SLASH_COMMENT_END: LINE_BREAK -> popMode;
SLASH_THE_REST: . -> skip;

mode IN_STAR;
STAR_COMMENT_NEST: '/*' -> pushMode(IN_NESTED_STAR), skip;
STAR_COMMENT_END: '*/' -> popMode;
STAR_THE_REST: . -> skip;

mode IN_NESTED_STAR;
NESTED_STAR_START: '/*' -> pushMode(IN_NESTED_STAR), skip;
NESTED_STAR_END: '*/' -> popMode, skip;
NESTED_STAR_THE_REST: . -> skip;

mode IN_CHAR;
CH_END: '\'' -> popMode, skip;
CH_ESC_END: (~'\'' | '\\\'' | DOUBLE_SLASH) -> skip;

mode IN_STRING;
STR_END: '"' -> popMode, skip;
STR_ESC_END: (~'"' | '\\"' | DOUBLE_SLASH) -> skip;

mode IN_BLOCK;
BLOCK_END: '"""' -> popMode, skip;
BLOCK_ESC_END: (. | '\\"' | DOUBLE_SLASH) -> skip;
