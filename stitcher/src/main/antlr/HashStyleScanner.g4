lexer grammar HashStyleScanner;

fragment LINE_BREAK: '\r'|'\n'|'\r\n';
fragment DOUBLE_SLASH: '\\\\';

HASH_COMMENT_START: '#' -> pushMode(IN_HASH);

SINGLE_QUOTE: '\'' -> pushMode(IN_CHAR), skip;
DOUBLE_QUOTE: '"' -> pushMode(IN_STRING), skip;
SINGLE_BLOCK_QUOTE: '\'\'\'' -> pushMode(IN_CHAR_BLOCK), skip;
DOUBLE_BLOCK_QUOTE: '"""' -> pushMode(IN_STRING_BLOCK), skip;

THE_REST: . -> skip;

mode IN_HASH;
HASH_COMMENT_END: LINE_BREAK -> popMode;
HASH_THE_REST: . -> skip;

mode IN_CHAR;
CH_END: ('\'' | LINE_BREAK) -> popMode, skip;
CH_ESC_END: ~['\r\n] -> skip;

mode IN_STRING;
STR_END: ('"' | LINE_BREAK) -> popMode, skip;
STR_ESC_END: (~["\r\n] | '\\"' | DOUBLE_SLASH) -> skip;

mode IN_CHAR_BLOCK;
CHAR_BLOCK_END: '\'\'\'' -> popMode, skip;
CHAR_BLOCK_REST: . -> skip;

mode IN_STRING_BLOCK;
STR_BLOCK_END: '"""' -> popMode, skip;
STR_BLOCK_ESC_END: (. | '\\"' | DOUBLE_SLASH) -> skip;
