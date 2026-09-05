package com.wassimbeltaief.loupe.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.messageCollector

@OptIn(ExperimentalCompilerApi::class)
class LoupePlugin : CompilerPluginRegistrar() {

    // Declares support for the K2 compiler (Kotlin 2.x). Without this the
    // plugin is silently skipped on K2 builds.
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messageCollector = configuration.messageCollector
        IrGenerationExtension.registerExtension(LoupeIrGenerationExtension(messageCollector))
    }
}
