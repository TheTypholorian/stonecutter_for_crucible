# Stonecutter
<a href="https://stonecutter.kikugie.dev/"><img alt="website" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/website_vector.svg"></a>
<a href="https://stonecutter.kikugie.dev/"><img alt="website" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/website_vector.svg"></a>
<a href="https://discord.kikugie.dev"><img alt="discord-singular" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy-minimal/social/discord-singular_vector.svg"></a>
<a href="https://ko-fi.com/kikugie"><img alt="discord-singular" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy-minimal/social/discord-singular_vector.svg"></a>

Stonecutter is a Gradle plugin that allows working on a project that targets multiple compatible release versions of a dependency.  
It's mostly meant for mods Minecraft mods, but on itself the plugin is platform-independent.

This project is inspired by [Preprocessor](https://github.com/ReplayMod/preprocessor), [JCP](https://github.com/raydac/java-comment-preprocessor) and [The original Stonecutter](https://github.com/SHsuperCM/Stonecutter), 
expanding on their features and providing new ones.

## Features
Stonecutter works by parsing marked comments in source files as its directives.
With a model of each file, it can transform it by disabling segments or replacing snippets
to adjust it for each supported version.  
It runs as a pre-processor, meaning it doesn't require any compiler or IDE extensions out of the box.
By default, Java, Kotlin, and various resource files are supported, but it can be extended to
virtually any file format that has comments.

### Conditions
Conditions are a basic tool in multi-versioning, as they can enable or disable segments based on a predicate.
Stonecutter conditions are very flexible, supporting semantic version comparisons, 
user-defined constants, boolean operators, and more.
```java
//? if >=1.21 <26.1 *check the version predicate, and comment out next line if it fails*
Clazz.callMeIn1_21();

//? if fabric { *check the mod loader constant, and comment out text until '}'*
FabricPlatform.method();
//?} else if neoforge *condition extensions are also supported*
//NeoForgePlatform.method();
```

### Replacements
Replacements substitute found matches in text with new values.
They can be defined globally for all files, or for specific segments in individual files.
```java
//~ if >=26.1 'GuiGraphics' -> 'GuiGraphicsExtractor'
import net.minecraft.client.gui.GuiGraphicsExtractor;
```

### Swaps
Swaps replace affected segments entirely with new values.
They can be useful to avoid repeating conditions, or to add compile-time constants.
```java
public static final String MINECRAFT = /*$ mc_version*/ "1.21.1";
```

## Quality of Life
Apart from syntax, Stonecutter offers plenty of QoL features,
such as version predicate checks in build scripts, structured properties, 
ordered tasks, and more documented on the website.
