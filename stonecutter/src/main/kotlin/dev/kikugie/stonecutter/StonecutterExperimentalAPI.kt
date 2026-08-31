package dev.kikugie.stonecutter

import kotlin.annotation.AnnotationTarget.*

/**
 * Marks members as experimental.
 *
 * During the experimental development phase, features may receive backwards-incompatible changes.
 */
@Target(CLASS, TYPEALIAS, FUNCTION, PROPERTY, CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn("This API is in development and may receive incompatible changes.", RequiresOptIn.Level.WARNING)
public annotation class StonecutterExperimentalAPI
