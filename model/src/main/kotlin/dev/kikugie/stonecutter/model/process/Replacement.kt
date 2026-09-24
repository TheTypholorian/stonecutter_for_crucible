package dev.kikugie.stonecutter.model.process

import dev.kikugie.stonecutter.model.Identifier
import kotlinx.serialization.Serializable

@Serializable
public sealed interface Replacement {
    public val identifier: Identifier?
}
