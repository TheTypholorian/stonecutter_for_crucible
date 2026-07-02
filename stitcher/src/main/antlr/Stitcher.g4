grammar Stitcher;

/* PARSER */
definition
    : COND_MARK condition EOF   # conditionDefinition
    | SWAP_MARK swap EOF        # swapDefinition
    | REPL_MARK replacement EOF # replacementDefinition
    ;

condition
    : SUGAR_IF? conditionExpression scopeOpener?                            # openerCondition
    | (SCOPE_CLOSE elseSugar? | elseSugar) conditionExpression scopeOpener? # extensionCondition
    | SCOPE_CLOSE? SUGAR_ELSE scopeOpener?                                  # elseExtCondition
    | SCOPE_CLOSE                                                           # closerCondition
    ;

swap
    : IDENTIFIER swapArguments? scopeOpener?               # identifierSwap
    | swapPrimary swapExtension* swapFallback scopeOpener? # localSwap
    | SCOPE_CLOSE                                          # closerSwap
    ;

replacement
    : replacementToggle+                                                         # toggleReplacement
    | SUGAR_IF conditionExpression replacementEntry+ (scopeOpener | scopeNamed)? # localReplacement
    | SCOPE_CLOSE                                                                # closerReplacement
    ;

scopeOpener
    : SCOPE_OPEN                    # closedScopeOpener
    | SCOPE_LOOKUP (PLUS? literal)? # lookupScopeOpener
    ;

scopeNamed
    : SCOPE_AS IDENTIFIER
    ;

elseSugar: SUGAR_ELSE SUGAR_IF | SUGAR_ELIF | SUGAR_ELSE;

swapArguments: (literal COMMA?)+;

swapPrimary: SUGAR_IF conditionExpression literal;

swapExtension: elseSugar conditionExpression literal;

swapFallback: SUGAR_ELSE literal;

replacementToggle: OP_NOT? IDENTIFIER COMMA?;

replacementEntry: literal OP_DIR literal COMMA?;

// This is order-sensitive
conditionExpression
    : IDENTIFIER                                                  # constantExpression
    | (IDENTIFIER OP_ASSIGN)? versionPredicate+                   # assignmentExpression
    | OP_NOT conditionExpression                                  # unaryExpression
    | LEFT_BRACE conditionExpression RIGHT_BRACE                  # groupExpression
    | conditionExpression op=(OP_AND | OP_OR) conditionExpression # binaryExpression
    ;

versionPredicate
    : (stringComparator | semanticComparator)? semanticVersion # semanticPredicate
    | stringComparator stringVersion?                          # stringPredicate
    ;

semanticVersion
    : versionCore (DASH preRelease)? (PLUS buildMetadata)?
    ;

stringVersion
    : IDENTIFIER
    ;

semanticComparator
    : REPL_MARK
    | COMP_MAJOR
    ;

stringComparator
    : COMP_EQUAL
    | COMP_NEQUAL
    | COMP_MORE
    | COMP_GMORE
    | COMP_LESS
    | COMP_GLESS
    ;

versionCore: NUMERIC (DOT NUMERIC)*;
preRelease: metadata (DASH metadata)*;
buildMetadata: metadata (DOT metadata)*;

metadata: NUMERIC | IDENTIFIER;
literal: IDENTIFIER | QUOTED;

/* LEXER */
fragment NUMBER: '0'|[1-9][0-9]*;
fragment IDENTIFIER_START: [_a-zA-Z];
fragment IDENTIFIER_PART: [_\-a-zA-Z0-9];

fragment ESC_SLASH: '\\\\';
fragment ESC_STAR: '\\*';
fragment ESC_TICK: '\\\'';

COND_MARK: '?';
SWAP_MARK: '$';
REPL_MARK: '~';

DOT: '.';
DASH: '-';
PLUS: '+';
COMMA: ',';

LEFT_BRACE: '(';
RIGHT_BRACE: ')';

SCOPE_CLOSE: '}';
SCOPE_OPEN: '{';
SCOPE_LOOKUP: '>>';
SCOPE_AS: 'as';

SUGAR_IF: 'if';
SUGAR_ELSE: 'else';
SUGAR_ELIF: 'elif';

COMP_MAJOR: '^';
COMP_EQUAL: '=';
COMP_NEQUAL: '!=';
COMP_MORE: '>';
COMP_GMORE: '>=';
COMP_LESS: '<';
COMP_GLESS: '<=';

OP_ASSIGN: ':';
OP_NOT: '!';
OP_AND: '&&';
OP_OR: '||';
OP_DIR: '->';

NUMERIC: NUMBER;
IDENTIFIER: IDENTIFIER_START IDENTIFIER_PART*;
QUOTED: '\'' (ESC_SLASH | ESC_TICK | ~[\\'])* '\'';
COMMENT: '*' (ESC_SLASH | ESC_STAR | ~[\\*])* '*' -> channel(HIDDEN);
WHITESPACE: [ \t\n\r]+ -> channel(HIDDEN);
INVALID: ~[^];
