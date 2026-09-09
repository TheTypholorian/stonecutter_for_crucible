package dev.kikugie.stonecutter

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.*

/** Marks an [Identifier] as the name of a constant. */
@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class ConstantID

/** Marks an [Identifier] as the name of a target. */
@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class TargetID

/** Marks an [Identifier] as the name of a swap. */
@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class SwapID

/** Marks an [Identifier] as the name of a replacement. */
@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class ReplacementID
