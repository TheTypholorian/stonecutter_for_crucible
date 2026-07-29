lexer grammar SwapTemplateLexer;

fragment NUMBER: [1-9][0-9]*;

PLACEHOLDER
    : '$' NUMBER
    | '${' NUMBER '}'
    ;
