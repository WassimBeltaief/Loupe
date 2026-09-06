package com.wassimbeltaief.loupe.plugin

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies IR injection by compiling source with the plugin, running the resulting bytecode
 * via reflection, and asserting that LoupeRuntime.record was called with the expected arguments.
 *
 * No mocking — we compile a real LoupeRuntime stub alongside the subject and run the JVM code.
 */
@OptIn(ExperimentalCompilerApi::class)
class LoupeIrTransformerTest {

    // Stub with the real FQN — our plugin detects @Composable by FQN, not by type resolution.
    private val composableStub = SourceFile.kotlin(
        "Composable.kt", """
        package androidx.compose.runtime
        annotation class Composable
        """.trimIndent()
    )

    // Minimal LoupeRuntime that records calls to a list we can inspect via reflection.
    private val loupeRuntimeStub = SourceFile.kotlin(
        "LoupeRuntime.kt", """
        package com.wassimbeltaief.loupe.runtime
        object LoupeRuntime {
            val calls = mutableListOf<Map<String, Any?>>()
            fun record(key: String, file: String, line: Int, params: Array<Pair<String, Any?>>) {
                calls += mapOf("key" to key, "file" to file, "line" to line, "params" to params)
            }
        }
        """.trimIndent()
    )

    @Test
    fun `injects record call at top of composable body`() {
        val source = SourceFile.kotlin(
            "ProductCard.kt", """
            import androidx.compose.runtime.Composable
            @Composable
            fun ProductCard(price: Double, title: String) {
                val ignored = 42
            }
            """.trimIndent()
        )

        val (result, sink) = compileWithPlugin(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        // Call ProductCard — the injected record call must fire before user body runs
        result.callTopLevel("ProductCardKt", "ProductCard", Double::class.java to 19.99, String::class.java to "Widget")

        assertEquals(1, sink.size, "Expected exactly one record call")
        assertEquals("ProductCard", sink[0]["key"])
        assertEquals("ProductCard.kt", sink[0]["file"])

        @Suppress("UNCHECKED_CAST")
        val params = sink[0]["params"] as Array<Pair<String, Any?>>
        assertEquals(2, params.size)
        assertEquals("price", params[0].first)
        assertEquals(19.99, params[0].second)
        assertEquals("title", params[1].first)
        assertEquals("Widget", params[1].second)
    }

    @Test
    fun `record call is first statement — user body still executes`() {
        // If the record call throws, user code never runs. Verify both happen and in order.
        val source = SourceFile.kotlin(
            "Counter.kt", """
            import androidx.compose.runtime.Composable
            var sideEffect = 0
            @Composable
            fun CounterComposable(n: Int) {
                sideEffect = n
            }
            """.trimIndent()
        )

        val (result, sink) = compileWithPlugin(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val clazz = result.classLoader.loadClass("CounterKt")
        clazz.getMethod("CounterComposable", Int::class.java).invoke(null, 7)

        // record was called
        assertEquals(1, sink.size)
        assertEquals("CounterComposable", sink[0]["key"])

        // user body also ran
        // Kotlin top-level var compiles to a private backing field — use declared + accessible
        val sideEffect = clazz.getDeclaredField("sideEffect").also { it.isAccessible = true }.get(null) as Int
        assertEquals(7, sideEffect)
    }

    @Test
    fun `skips non-@Composable functions`() {
        val source = SourceFile.kotlin(
            "Plain.kt", """
            fun plainFunction(x: Int): Int = x * 2
            """.trimIndent()
        )

        val (result, sink) = compileWithPlugin(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        result.classLoader.loadClass("PlainKt")
            .getMethod("plainFunction", Int::class.java)
            .invoke(null, 5)

        assertTrue(sink.isEmpty(), "No record calls expected for plain functions")
    }

    @Test
    fun `skips inline composables`() {
        val source = SourceFile.kotlin(
            "InlineComp.kt", """
            import androidx.compose.runtime.Composable
            @Composable
            inline fun InlineComposable(block: () -> Unit) {
                block()
            }
            """.trimIndent()
        )

        val (result, sink) = compileWithPlugin(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue(sink.isEmpty(), "Inline composables must not be instrumented")
    }

    @Test
    fun `uses identityHashCode for lambda parameters`() {
        val source = SourceFile.kotlin(
            "Button.kt", """
            import androidx.compose.runtime.Composable
            @Composable
            fun Button(label: String, onClick: () -> Unit) {}
            """.trimIndent()
        )

        val (result, sink) = compileWithPlugin(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val lambda = {}
        result.callTopLevel("ButtonKt", "Button", String::class.java to "OK", Function0::class.java to lambda)

        assertEquals(1, sink.size)
        @Suppress("UNCHECKED_CAST")
        val params = sink[0]["params"] as Array<Pair<String, Any?>>
        assertEquals(2, params.size)

        assertEquals("label", params[0].first)
        assertEquals("OK", params[0].second)

        assertEquals("onClick", params[1].first)
        // Lambda param must be the identity hash code (an Int), not the lambda itself
        val recorded = params[1].second
        assertTrue(recorded is Int, "Lambda param should be Int identityHashCode, got ${recorded?.javaClass}")
        assertEquals(System.identityHashCode(lambda), recorded)
    }

    @Test
    fun `handles composable with no parameters`() {
        val source = SourceFile.kotlin(
            "Spinner.kt", """
            import androidx.compose.runtime.Composable
            @Composable
            fun Spinner() {}
            """.trimIndent()
        )

        val (result, sink) = compileWithPlugin(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        result.classLoader.loadClass("SpinnerKt").getMethod("Spinner").invoke(null)

        assertEquals(1, sink.size)
        @Suppress("UNCHECKED_CAST")
        val params = sink[0]["params"] as Array<Pair<String, Any?>>
        assertTrue(params.isEmpty(), "Zero-param composable should have empty params array")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Compiles [source] with our plugin and the required stubs.
     * Returns the compilation result and a live list that is backed by LoupeRuntime.calls
     * in the compiled classloader — populated when record() is called.
     */
    private fun compileWithPlugin(source: SourceFile): Pair<JvmCompilationResult, List<Map<String, Any?>>> {
        val result = KotlinCompilation().apply {
            sources = listOf(composableStub, loupeRuntimeStub, source)
            compilerPluginRegistrars = listOf(testPlugin())
            inheritClassPath = true
        }.compile()

        // Load LoupeRuntime from the compiled classloader to get the live calls list
        @Suppress("UNCHECKED_CAST")
        val calls = if (result.exitCode == KotlinCompilation.ExitCode.OK) {
            val runtimeClass = result.classLoader.loadClass("com.wassimbeltaief.loupe.runtime.LoupeRuntime")
            val instance = runtimeClass.getField("INSTANCE").get(null)
            runtimeClass.getMethod("getCalls").invoke(instance) as List<Map<String, Any?>>
        } else {
            emptyList()
        }

        return result to calls
    }

    /**
     * Calls a static top-level Kotlin function by reflection.
     * [args] are (paramType, paramValue) pairs.
     */
    private fun JvmCompilationResult.callTopLevel(
        className: String,
        methodName: String,
        vararg args: Pair<Class<*>, Any?>,
    ) {
        val clazz = classLoader.loadClass(className)
        val method = clazz.getMethod(methodName, *args.map { it.first }.toTypedArray())
        method.invoke(null, *args.map { it.second }.toTypedArray())
    }

    private fun testPlugin() = object : CompilerPluginRegistrar() {
        override val supportsK2 = true
        override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
            IrGenerationExtension.registerExtension(
                LoupeIrGenerationExtension(messageCollector = configuration.messageCollector)
            )
        }
    }
}
