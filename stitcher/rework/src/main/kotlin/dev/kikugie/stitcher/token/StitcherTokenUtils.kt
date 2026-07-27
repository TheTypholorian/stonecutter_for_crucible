package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.antlr.AntlrToken

val StitcherToken?.typeValue: Int
    inline get() = this?.type?.value ?: AntlrToken.EOF
