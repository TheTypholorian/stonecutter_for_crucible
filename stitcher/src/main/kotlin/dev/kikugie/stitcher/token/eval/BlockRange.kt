package dev.kikugie.stitcher.token.eval

import dev.kikugie.stitcher.token.impl.BlockToken

val BlockToken.Code.scopeStart: Int
    get() = scope.firstOrNull()?.start ?: host.end

val BlockToken.Code.scopeEnd: Int
    get() = scope.lastOrNull()?.end ?: host.end