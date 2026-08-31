package dev.kikugie.stonecutter

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.*

@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class ConstantID

@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class TargetID

@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class SwapID

@Target(VALUE_PARAMETER, LOCAL_VARIABLE, PROPERTY, FIELD, TYPE)
@Retention(BINARY)
public annotation class ReplacementID
