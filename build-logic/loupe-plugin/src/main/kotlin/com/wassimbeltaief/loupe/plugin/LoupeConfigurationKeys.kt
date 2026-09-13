package com.wassimbeltaief.loupe.plugin

import org.jetbrains.kotlin.config.CompilerConfigurationKey

/** Keys used to pass Gradle options to the compiler plugin. */
object LoupeConfigurationKeys {

    /** Packages to instrument, parsed from the ';'-separated `packageFilter` option. */
    val KEY_PACKAGE_FILTER: CompilerConfigurationKey<List<String>> =
        CompilerConfigurationKey.create("loupe.packageFilter")
}
