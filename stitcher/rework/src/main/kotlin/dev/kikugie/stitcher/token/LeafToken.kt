package dev.kikugie.stitcher.token

import dev.kikugie.stitcher.document.DocumentReference
import dev.kikugie.stitcher.token.type.StitcherTokenType
import dev.kikugie.stitcher.util.OpenIntRange

class LeafToken(override val type: StitcherTokenType, val ref: DocumentReference) : StitcherToken, OpenIntRange by ref {
    val text: String inline get() = ref.text
}
