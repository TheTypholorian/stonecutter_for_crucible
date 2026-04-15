import dev.kikugie.semver.data.*

assert(sc.properties.get<Boolean>("bool_prop") == true) { "'bool_prop' is not true" }
assert(sc.properties.get<Byte>("num_prop") == 10.toByte()) { "'num_prop' byte is not 10" }
assert(sc.properties.get<Short>("num_prop") == 10.toShort()) { "'num_prop' short is not 10" }
assert(sc.properties.get<Int>("num_prop") == 10) { "'num_prop' int is not 10" }
assert(sc.properties.get<Long>("num_prop") == 10L) { "'num_prop' long is not 10" }
assert(sc.properties.get<Float>("float_prop") == 3.7F) { "'float_prop' float is not 3.7" }
assert(sc.properties.get<Double>("float_prop") == 3.7) { "'float_prop' double is not 3.7" }
assert(sc.properties.get<String>("str_prop") == "hello") { "'str_prop' string is not 'hello'" }
assert(sc.properties.get<Version>("str_prop") == StringVersion("hello")) { "'str_prop' version is not 'hello'" }
assert(sc.properties.get<StringVersion>("str_prop") == StringVersion("hello")) { "'str_prop' string version is not 'hello'" }
assert(sc.properties.get<SemanticVersion>("vers_prop") == SemanticVersion(1, 0, 0)) { "'vers_prop' is not 1.0.0" }