import dev.kikugie.stitcher.replacement.template.InsertionPlace
import dev.kikugie.stitcher.replacement.template.findInsertionPlaces
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.shouldBe

class TemplateLexerTest : AnnotationSpec() {
    @Test
    fun `locate insertion places`() {
        val text = $$"..$a..$0..$1..$0x10..$001..$100..\t100"
        val places = listOf(
            InsertionPlace(1, 10, 12),
            InsertionPlace(100, 27, 31),
        )
        text.findInsertionPlaces().joinToString("\n") shouldBe places.joinToString("\n")
    }
}