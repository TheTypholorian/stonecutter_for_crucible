package dev.kikugie.stonecutter.build.config

import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.TargetID
import dev.kikugie.stonecutter.data.whatever.ExtendedMapProperty
import org.gradle.api.provider.MapProperty
import javax.inject.Inject

/**[Stonecutter dependency](https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#targets) configuration extension.*/
@Deprecated("Stonecutter dependencies were renamed to targets", ReplaceWith("TargetContainer"))
public abstract class DependencyContainer @Inject constructor(delegate: TargetContainer) :
    ExtendedMapProperty<@TargetID Identifier, AnyVersion>, MapProperty<@TargetID Identifier, AnyVersion> by delegate
