package dev.kikugie.stonecutter

import kotlin.annotation.AnnotationTarget.*

@RequiresOptIn("This API is internal to Stonecutter and should not be used.", RequiresOptIn.Level.WARNING)
@Target(CLASS, TYPEALIAS, FUNCTION, PROPERTY, CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
internal annotation class StonecutterInternalAPI
