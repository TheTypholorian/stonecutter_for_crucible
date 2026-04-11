package unit

import dev.kikugie.stonecutter.data.ProjectHierarchy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe

class ProjectHierarchyTest : FunSpec({
    context("size") {
        test("root") {
            ProjectHierarchy(":").size shouldBe 0
        }

        test("path") {
            ProjectHierarchy(":core:sub").size shouldBe 2
        }
    }

    context("starts with") {
        test("from root") {
            val a = ProjectHierarchy(":")
            val b = ProjectHierarchy(":core")
            b.startsWith(a) shouldBe true
        }

        test("from part") {
            val a = ProjectHierarchy(":core")
            val b = ProjectHierarchy(":core:sub")
            b.startsWith(a) shouldBe true
        }

        test("from self") {
            val a = ProjectHierarchy(":core")
            val b = ProjectHierarchy(":core")
            b.startsWith(a) shouldBe true
        }

        test("to root") {
            val a = ProjectHierarchy(":core")
            val b = ProjectHierarchy(":")
            b.startsWith(a) shouldBe false
        }

        test("to short") {
            val a = ProjectHierarchy(":core:sub")
            val b = ProjectHierarchy(":core")
            b.startsWith(a) shouldBe false
        }

        test("from partial") {
            val a = ProjectHierarchy(":cor")
            val b = ProjectHierarchy(":core")
            b.startsWith(a) shouldBe false
        }
    }

    context("relativize") {
        test("from root") {
            val a = ProjectHierarchy(":")
            val b = ProjectHierarchy(":core")
            a.relativize(b) shouldBe "core"
        }

        test("from part") {
            val a = ProjectHierarchy(":core")
            val b = ProjectHierarchy(":core:sub")
            a.relativize(b) shouldBe "sub"
        }

        test("from self") {
            val a = ProjectHierarchy(":core")
            val b = ProjectHierarchy(":core")
            a.relativize(b) shouldBe ""
        }

        test("from diff") {
            val a = ProjectHierarchy(":core")
            val b = ProjectHierarchy(":encore")
            a.relativize(b) shouldBe null
        }
    }

    context("contains") {
        test("segment") {
            ("core" in ProjectHierarchy(":core:sub")) shouldBe true
        }

        test("joined") {
            ("core:sub" in ProjectHierarchy(":core:sub")) shouldBe true
        }

        test("incomplete start") {
            ("ore:sub" in ProjectHierarchy(":core:sub")) shouldBe false
        }

        test("incomplete end") {
            ("core:su" in ProjectHierarchy(":core:sub")) shouldBe false
        }
    }

    context("get") {
        test("regular") {
            ProjectHierarchy(":core:sub")[1] shouldBe "sub"
        }

        test("oob empty") {
            shouldThrow<IndexOutOfBoundsException> { ProjectHierarchy(":")[0] }
        }

        test("oob negative") {
            shouldThrow<IndexOutOfBoundsException> { ProjectHierarchy(":core:sub")[-1] }
        }

        test("oob large") {
            shouldThrow<IndexOutOfBoundsException> { ProjectHierarchy(":core:sub")[2] }
        }
    }

    context("index of") {
        test("segment") {
            ProjectHierarchy(":core:sub").indexOf("sub") shouldBe 1
        }

        test("joined") {
            ProjectHierarchy(":core:sub").indexOf("sub:two") shouldBe -1
            ProjectHierarchy(":core:sub:two").indexOf("sub:two") shouldBe 1
        }

        test("duplicate") {
            ProjectHierarchy(":core:sub:sub").indexOf("sub") shouldBe 1
        }

        test("incomplete") {
            ProjectHierarchy(":core:sub").indexOf("su") shouldBe -1
        }
    }

    context("last index of") {
        test("segment") {
            ProjectHierarchy(":core:sub:sub").lastIndexOf("sub") shouldBe 2
        }

        test("joined") {
            ProjectHierarchy(":core:sub:two:sub:two").lastIndexOf("sub:two") shouldBe 3
        }

        test("incomplete") {
            ProjectHierarchy(":core:sub:su").indexOf("sub") shouldBe 1
        }
    }

    context("iterator") {
        test("to list") {
            ProjectHierarchy(":core:sub:two") shouldContainInOrder listOf("core", "sub", "two")
        }
    }
})
