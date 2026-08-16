plugins {
    id("dev.kikugie.stonecutter")
}

check(sc.properties["option"] == "0") { "${sc.properties["option"]} != 0" }

sc.properties.tags("tag2")
check(sc.properties["option"] == "2") { "${sc.properties["option"]} != 2 #1" }

sc.properties.tags("tag1")
check(sc.properties["option"] == "2") { "${sc.properties["option"]} != 2 #2" }
