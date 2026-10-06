package dev.kikugie.stonecutter.build.config

import dev.kikugie.stonecutter.VersionString
import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.Identifier
import dev.kikugie.stonecutter.TargetID
import dev.kikugie.stonecutter.data.collection.ExtendedMapProperty
import org.gradle.api.provider.MapProperty
import javax.inject.Inject

/**[Stonecutter dependency](https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#targets) configuration extension.*/
@Deprecated("Stonecutter dependencies were renamed to targets", ReplaceWith("TargetContainer"))
public abstract class DependencyContainer @Inject constructor(delegate: TargetContainer) :
    ExtendedMapProperty<@TargetID Identifier, @VersionString AnyVersion>, MapProperty<@TargetID Identifier, @VersionString AnyVersion> by delegate
