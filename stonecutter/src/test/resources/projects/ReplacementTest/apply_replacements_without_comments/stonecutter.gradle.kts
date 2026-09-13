plugins {
    id("dev.kikugie.stonecutter")
}

sc active "1"

// `txt` has no comment syntax, so the handler leaves the scanner and the commenter unset.
stonecutter handlers {
    configure("txt") {}
}
