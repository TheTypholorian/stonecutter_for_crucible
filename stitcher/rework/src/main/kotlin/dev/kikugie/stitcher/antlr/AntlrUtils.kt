package dev.kikugie.stitcher.antlr

import org.antlr.v4.runtime.ANTLRErrorListener
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token

typealias AntlrToken = Token

var Recognizer<*, *>.errorListener: ANTLRErrorListener
    get() = errorListeners.single()
    set(value) {
        errorListeners.clear()
        errorListeners as MutableList<ANTLRErrorListener> += value
    }
