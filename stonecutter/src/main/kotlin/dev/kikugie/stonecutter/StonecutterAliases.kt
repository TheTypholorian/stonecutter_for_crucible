package dev.kikugie.stonecutter

/**
 * A [String] that should represent a valid identifier,
 * matching the pattern `[_a-zA-Z][_\-a-zA-Z0-9]*`.
 *
 * This type is used for naming subrpojects and parameters
 * like constants, swaps, etc.
 *
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/glossary#identifier">Glossary</a>
 */
public typealias Identifier = String

/**
 * A [String] that should represent either a valid [SemanticVersion][dev.kikugie.semver.data.SemanticVersion]
 * or [StringVersion][dev.kikugie.semver.data.StringVersion].
 *
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/syntax/conditions#version-types">Version Types Documentation</a>
 */
public typealias AnyVersion = String

/**
 * Represents either a [String] or [ProjectDescriptor][org.gradle.api.initialization.ProjectDescriptor].
 *
 * @see <a href="https://stonecutter.kikugie.dev/wiki/v2/reference/gradle-api/project-model#tree">Project Tree Documentation</a>
 */
public typealias ProjectReference = Any