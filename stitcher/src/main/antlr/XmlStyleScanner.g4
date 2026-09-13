lexer grammar XmlStyleScanner;

XML_COMMENT_START: '<!--' -> pushMode(IN_COMMENT);
THE_REST: . -> skip;

mode IN_COMMENT;
XML_COMMENT_END: '-->' -> popMode;
COMMENT_THE_REST: . -> skip;
