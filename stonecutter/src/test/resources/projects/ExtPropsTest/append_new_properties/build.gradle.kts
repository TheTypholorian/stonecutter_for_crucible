check(sc.properties.contains("prop1")) { "Missing prop1" }
check(!sc.properties.contains("prop2")) { "Resolved prop2" }

sc properties rootDir.resolve("other.properties.toml")
check(sc.properties.contains("prop2")) { "Missing prop2" }
