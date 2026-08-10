package dev.kikugie.stonecutter.build.config

import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.data.whatever.ExtendedMapProperty
import org.gradle.api.provider.MapProperty
import javax.inject.Inject

/**[Stonecutter target](https://stonecutter.kikugie.dev/wiki/config/params#condition-dependencies) configuration extension.*/
@Deprecated("Stonecutter dependencies were renamed to targets", ReplaceWith("TargetContainer"))
public abstract class DependencyContainer @Inject constructor(delegate: TargetContainer) :
    ExtendedMapProperty<Identifier, AnyVersion>, MapProperty<Identifier, AnyVersion> by delegate
