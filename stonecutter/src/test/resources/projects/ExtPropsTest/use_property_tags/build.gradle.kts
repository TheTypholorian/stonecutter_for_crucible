import org.gradle.kotlin.dsl.extra

check(!extra.has("my_property")) { "`my_property` isn't loaded yet" }

stonecutter properties {
    tags("example_tag")
}

check(extra.has("my_property")) { "`my_property` should be loaded" }
