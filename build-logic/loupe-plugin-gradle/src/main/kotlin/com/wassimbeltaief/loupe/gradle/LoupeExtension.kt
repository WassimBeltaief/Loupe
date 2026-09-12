package com.wassimbeltaief.loupe.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * DSL: loupe { packageFilter.set(listOf("com.mycompany.myapp")); enabled.set(false) }
 *
 * Registered by [LoupeGradlePlugin] as the `loupe` extension.
 */
abstract class LoupeExtension {

    /**
     * Packages whose composables are instrumented.
     * Empty (default) = the module's Android namespace, i.e. the app's own code only.
     */
    abstract val packageFilter: ListProperty<String>

    /** Master switch. When false, the compiler plugin is not applied to any compilation. */
    abstract val enabled: Property<Boolean>

    init {
        packageFilter.convention(emptyList())
        enabled.convention(true)
    }
}
