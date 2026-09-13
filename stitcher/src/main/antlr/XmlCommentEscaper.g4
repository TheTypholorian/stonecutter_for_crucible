lexer grammar XmlCommentEscaper;

// XML forbids `--` inside a comment, so `XmlCommentingStrategy` escapes enclosed markers to `<!^^`/`^^>`.

XML_ESCAPED_START: '<!^^' -> pushMode(IN_ESCAPED);
THE_REST: . -> skip;

mode IN_ESCAPED;
XML_ESCAPED_NEST: '<!^^' -> pushMode(IN_ESCAPED), type(XML_ESCAPED_START);
XML_ESCAPED_END: '^^>' -> popMode;
ESCAPED_THE_REST: . -> skip;
