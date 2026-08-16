check(!stonecutter.properties.contains("my_property")) { "`my_property` isn't loaded yet" }

stonecutter properties {
    tags("example_tag")
}

check(stonecutter.properties.contains("my_property")) { "`my_property` should be loaded" }
