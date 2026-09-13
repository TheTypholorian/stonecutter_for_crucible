lexer grammar PlainTextScanner;

// A scanner for formats with no comment syntax. It never emits an opener or a closer,
// so the whole document is resolved as a single content block by `ScannerAdapter`.
THE_REST: . -> skip;
