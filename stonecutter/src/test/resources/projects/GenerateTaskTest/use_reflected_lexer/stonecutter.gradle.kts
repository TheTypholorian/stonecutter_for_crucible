import dev.kikugie.stitcher.antlr.HashStyleScanner

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1"

stonecutter handlers {
    configure("note") {
        scanner {
            lexer.set(reflected<HashStyleScanner>())
            openers(HashStyleScanner.Tokens.HASH_COMMENT_START)
            closers(HashStyleScanner.Tokens.HASH_COMMENT_END)
        }
        commenter.set(line("#"))
    }
}
