plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1"

stonecutter handlers {
    configure("note") {
        scanner {
            lexer.set(
                interpreted(
                    """
                    lexer grammar NoteScanner;
                    NOTE_START: '%' -> pushMode(IN_NOTE);
                    THE_REST: . -> skip;
                    mode IN_NOTE;
                    NOTE_END: ('\r' | '\n' | '\r\n') -> popMode;
                    NOTE_THE_REST: . -> skip;
                    """.trimIndent()
                )
            )
            openers(1)
            closers(3)
        }
        commenter.set(line("%"))
    }
}
