package com.wassimbeltaief.loupe.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Wires the Loupe compiler plugin into debuggable compilations only.
 *
 * Hard constraint: never activate on release/staging or any
 * non-debuggable variant. We enforce this by inspecting the compilation name,
 * which for Android modules is the variant/build-type name ("debug", "release",
 * "freeDebug", …). Test compilations (unit/androidTest) are also skipped — the
 * app code under test is instrumented via its own debug compilation already.
 */
class LoupeGradlePlugin : KotlinCompilerPluginSupportPlugin {

    /** Registers the `loupe { }` extension. */
    override fun apply(project: Project) {
        project.extensions.create("loupe", LoupeExtension::class.java)
    }

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(groupId = GROUP_ID, artifactId = PLUGIN_ARTIFACT_ID, version = VERSION)

    /**
     * Decides whether the compiler plugin is applied to one compilation.
     *
     * The plugin is skipped on release and staging variants, and on test
     * compilations, because the app code is already instrumented through its own
     * debug compilation.
     */
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.findByType(LoupeExtension::class.java) ?: return false
        if (!extension.enabled.get()) return false

        val hasAndroidPlugin = project.pluginManager.hasPlugin("com.android.application") ||
            project.pluginManager.hasPlugin("com.android.library")
        // Pure Kotlin/JVM modules have no build variants — always instrument
        if (!hasAndroidPlugin) return true

        val compilationName = kotlinCompilation.name.lowercase()
        val isDebuggableVariant = "debug" in compilationName
        val isTestCompilation = compilationName.endsWith("unittest") || compilationName.endsWith("androidtest")
        return isDebuggableVariant && !isTestCompilation
    }

    /**
     * Passes the plugin options to the compiler.
     *
     * The package filter comes from the `loupe { }` extension. When it is empty,
     * the module namespace is used, so only the app's own code is instrumented.
     */
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val extension = project.extensions.getByType(LoupeExtension::class.java)
        return project.provider {
            val filter = extension.packageFilter.get().ifEmpty {
                listOfNotNull(project.androidNamespace())
            }
            listOf(SubpluginOption(OPTION_PACKAGE_FILTER, filter.joinToString(";")))
        }
    }

    // Default scope: the module's own namespace, which is the app's own code.
    private fun Project.androidNamespace(): String? =
        extensions.findByType(ApplicationExtension::class.java)?.namespace
            ?: extensions.findByType(LibraryExtension::class.java)?.namespace

    companion object {
        const val COMPILER_PLUGIN_ID = "com.wassimbeltaief.loupe"
        const val OPTION_PACKAGE_FILTER = "packageFilter"

        // Must match the coordinates declared in build-logic/loupe-plugin/build.gradle.kts
        const val GROUP_ID = "com.wassimbeltaief"
        const val PLUGIN_ARTIFACT_ID = "loupe-plugin"
        const val VERSION = "1.0.1"
    }
}
