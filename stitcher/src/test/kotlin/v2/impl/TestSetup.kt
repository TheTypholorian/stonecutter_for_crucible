package v2.impl

import io.kotest.core.spec.style.scopes.RootTestWithConfigBuilder
import io.kotest.core.spec.style.scopes.TestWithConfigBuilder
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestScope
import io.kotest.core.test.config.TestConfig

@DslMarker @Retention(AnnotationRetention.SOURCE)
private annotation class ExtendedTestDsl

@ExtendedTestDsl
fun interface TestSetup {
    suspend fun TestScope.invoke()
}

inline fun <reified T : TestSetup> RootTestWithConfigBuilder.config(
    config: TestConfig = TestConfig(),
    crossinline action: suspend context(TestScope) T.() -> Unit
): Unit = config(config) {
    with(instantiate(T::class.java, testCase).also { action(this, it) }) { invoke() }
}

suspend inline fun <reified T : TestSetup> TestWithConfigBuilder.config(
    config: TestConfig = TestConfig(),
    crossinline action: suspend context(TestScope) T.() -> Unit
): Unit = config(config) {
    with(instantiate(T::class.java, testCase).also { action(this, it) }) { invoke() }
}

fun <T : TestSetup> instantiate(cls: Class<T>, case: TestCase): T = try {
    cls.getConstructor(TestCase::class.java).run {
        isAccessible = true
        newInstance(case)
    }
} catch (_: NoSuchMethodException) {
    cls.getConstructor().run {
        isAccessible = true
        newInstance()
    }
}