import dev.kikugie.semver.data.*

// `get(name)` is the untyped `String` accessor; `getAs<T>(name)` is the converting one.
check(sc.properties.getAs<Boolean>("bool_prop") == true) { "'bool_prop' is not true" }
check(sc.properties.getAs<Byte>("num_prop") == 10.toByte()) { "'num_prop' byte is not 10" }
check(sc.properties.getAs<Short>("num_prop") == 10.toShort()) { "'num_prop' short is not 10" }
check(sc.properties.getAs<Int>("num_prop") == 10) { "'num_prop' int is not 10" }
check(sc.properties.getAs<Long>("num_prop") == 10L) { "'num_prop' long is not 10" }
check(sc.properties.getAs<UInt>("num_prop") == 10u) { "'num_prop' uint is not 10" }
check(sc.properties.getAs<Float>("float_prop") == 3.7F) { "'float_prop' float is not 3.7" }
check(sc.properties.getAs<Double>("float_prop") == 3.7) { "'float_prop' double is not 3.7" }
check(sc.properties.getAs<String>("str_prop") == "hello") { "'str_prop' string is not 'hello'" }
check(sc.properties.getAs<Version>("str_prop") == StringVersion("hello")) { "'str_prop' version is not 'hello'" }
check(sc.properties.getAs<StringVersion>("str_prop") == StringVersion("hello")) { "'str_prop' string version is not 'hello'" }
check(sc.properties.getAs<SemanticVersion>("vers_prop") == SemanticVersion(1, 0, 0)) { "'vers_prop' is not 1.0.0" }

// The untyped accessor still returns the raw text.
check(sc.properties["str_prop"] == "hello") { "'str_prop' raw value is not 'hello'" }

// A value that cannot be converted reports the property and the target type.
runCatching { sc.properties.getAs<Int>("str_prop") }
    .onSuccess { error("'str_prop' unexpectedly converted to Int") }
    .onFailure { check(it is IllegalArgumentException) { "Expected IllegalArgumentException, got $it" } }
