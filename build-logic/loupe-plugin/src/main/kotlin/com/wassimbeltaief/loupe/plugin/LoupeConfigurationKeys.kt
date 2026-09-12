package com.wassimbeltaief.loupe.plugin

import org.jetbrains.kotlin.config.CompilerConfigurationKey

object LoupeConfigurationKeys {

    /** Packages to instrument, parsed from the ';'-separated `packageFilter` option. */
    val KEY_PACKAGE_FILTER: CompilerConfigurationKey<List<String>> =
        CompilerConfigurationKey.create("loupe.packageFilter")
}
