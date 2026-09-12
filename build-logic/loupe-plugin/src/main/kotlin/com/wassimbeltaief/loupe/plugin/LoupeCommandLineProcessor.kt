package com.wassimbeltaief.loupe.plugin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Bridges Gradle-side [SubpluginOption]s (see `LoupeGradlePlugin`) into the
 * compiler's [CompilerConfiguration]. Option names must match exactly.
 */
@OptIn(ExperimentalCompilerApi::class)
class LoupeCommandLineProcessor : CommandLineProcessor {

    override val pluginId: String = PLUGIN_ID

    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = OPTION_PACKAGE_FILTER,
            valueDescription = "pkg1;pkg2",
            description = "Packages whose composables are instrumented (';'-separated). Empty = no filtering.",
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        when (option.optionName) {
            OPTION_PACKAGE_FILTER -> configuration.put(
                LoupeConfigurationKeys.KEY_PACKAGE_FILTER,
                value.split(';').map { it.trim() }.filter { it.isNotEmpty() },
            )
        }
    }

    companion object {
        const val PLUGIN_ID = "com.wassimbeltaief.loupe"
        const val OPTION_PACKAGE_FILTER = "packageFilter"
    }
}
