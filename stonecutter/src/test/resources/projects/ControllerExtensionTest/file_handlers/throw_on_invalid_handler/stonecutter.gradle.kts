plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1"

stonecutter handlers {
    // `inherit` requires the source extension to be registered; this one never is.
    inherit("not_a_registered_format", "custom")
}
