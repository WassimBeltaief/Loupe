package com.wassimbeltaief.loupe.plugin

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class LoupeIrVisitorTest {

    @Test
    fun `finds @Composable functions and skips regular functions`() {
        val source = SourceFile.kotlin(
            "ProductCard.kt", """
            import androidx.compose.runtime.Composable

            @Composable
            fun ProductCard(price: Double, title: String) {}

            @Composable
            fun SearchResultsList(items: List<String>) {}

            fun notAComposable() {}
        """.trimIndent()
        )

        val visited = mutableListOf<String>()

        val result = KotlinCompilation().apply {
            sources = listOf(composableStub, source)
            compilerPluginRegistrars = listOf(capturingPlugin(visited))
            inheritClassPath = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue("ProductCard" in visited, "Expected ProductCard in visited: $visited")
        assertTrue("SearchResultsList" in visited, "Expected SearchResultsList in visited: $visited")
        assertFalse("notAComposable" in visited, "notAComposable should not be visited: $visited")
    }

    @Test
    fun `skips non-@Composable functions entirely`() {
        val source = SourceFile.kotlin(
            "Plain.kt", """
            fun plainFunction(x: Int): Int = x * 2
        """.trimIndent()
        )

        val visited = mutableListOf<String>()

        val result = KotlinCompilation().apply {
            sources = listOf(source)
            compilerPluginRegistrars = listOf(capturingPlugin(visited))
            inheritClassPath = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue(visited.isEmpty(), "No composables expected: $visited")
    }

    // Creates a plugin variant that forwards found @Composable names into a list.
    // The production LoupePlugin uses the MessageCollector instead.
    private fun capturingPlugin(sink: MutableList<String>) = object : CompilerPluginRegistrar() {
        override val supportsK2 = true
        override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
            IrGenerationExtension.registerExtension(
                LoupeIrGenerationExtension(
                    messageCollector = configuration.messageCollector,
                    onComposableFound = { sink.add(it) }
                )
            )
        }
    }
}
