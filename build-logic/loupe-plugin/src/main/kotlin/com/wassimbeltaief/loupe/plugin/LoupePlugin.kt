package com.wassimbeltaief.loupe.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector

/**
 * The compiler plugin entry point.
 *
 * The Kotlin compiler loads this class through the service file in
 * `META-INF/services`. It reads the plugin options and registers the IR
 * transformer that instruments composables.
 */
@OptIn(ExperimentalCompilerApi::class)
class LoupePlugin : CompilerPluginRegistrar() {

    // Without this, the plugin is silently skipped on Kotlin 2.x (K2).
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messageCollector = configuration.messageCollector
        val packageFilter = configuration.get(LoupeConfigurationKeys.KEY_PACKAGE_FILTER, emptyList())
        IrGenerationExtension.registerExtension(
            LoupeIrGenerationExtension(messageCollector, packageFilter = packageFilter)
        )
    }
}
