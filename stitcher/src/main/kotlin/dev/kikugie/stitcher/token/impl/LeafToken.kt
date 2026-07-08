package dev.kikugie.stitcher.token.impl

import dev.kikugie.stitcher.document.Document
import dev.kikugie.stitcher.token.StitcherToken
import dev.kikugie.stitcher.token.StitcherType

data class LeafToken(
    val document: Document,
    override val type: StitcherType,
    override val start: Int,
    override val end: Int,
) : StitcherToken {
    private lateinit var _text: String

    var text: String
        get() {
            if (!::_text.isInitialized) _text = document[start, end]
            return _text
        }
        set(value) {
            _text = value
        }
}
