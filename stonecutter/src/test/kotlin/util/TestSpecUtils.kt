package util

import io.kotest.core.spec.style.scopes.RootTestWithConfigBuilder
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
    val instance = T::class.java.getConstructor().newInstance()
    with(instance.also { action(this, it) }) { invoke() }
}
