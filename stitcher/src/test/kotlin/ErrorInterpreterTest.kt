import dev.kikugie.stitcher.issue.parser.DefaultErrorInterpreters
import io.kotest.assertions.shouldFail
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.string.shouldContain
import util.processRaw

//class ErrorInterpreterTest : FunSpec({
//    cases(
//        DefaultErrorInterpreters.EMPTY_CONDITION_ID,
//        "//?",
//        "//? {",
//        "//? if",
//        "//? if {",
//        "//? if >>",
//        "//? elif",
//        "//? else if",
//        "//$ if",
//        "//$ if else 'A'",
//        "//$ if true elif",
//        "//~ if",
//        "//~ if {",
//        "//~ if 'A' -> 'B'",
//    )
//
//    cases(
//        DefaultErrorInterpreters.MISSING_CLOSER_ID,
//        "//? else",
//        "//? elif true",
//    )
//})

@Suppress("NOTHING_TO_INLINE")
private inline fun FunSpec.cases(id: String, vararg variants: String) = context(id) {
    withData(ts = variants.asIterable(), nameFn = { it.substringAfterLast("//") }) {
        shouldFail { processRaw(it) }.message shouldContain id
    }
}
