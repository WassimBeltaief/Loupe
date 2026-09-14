package com.wassimbeltaief.loupe.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * Prints the Loupe summary table after a connected test run.
 *
 * It is wired as a finalizer of every `connected*AndroidTest` task by
 * [LoupeGradlePlugin], so it runs even when the tests fail. The data is read
 * from AGP's `build/outputs/androidTest-results` XML, which AGP always writes.
 */
abstract class LoupeResultsTask : DefaultTask() {

    /** `build/outputs/androidTest-results`, where AGP drops `TEST-*.xml`. */
    @get:Internal
    abstract val resultsDir: DirectoryProperty

    init {
        group = "verification"
        description = "Prints a summary of Loupe recomposition test results."
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun report() {
        val dir = resultsDir.orNull?.asFile ?: return
        if (!dir.isDirectory) return

        val files = dir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("TEST-") && it.name.endsWith(".xml") }
            .toList()
        if (files.isEmpty()) return

        // Only report the run that just happened: AGP leaves XML files from
        // previous runs and other devices behind, and a run never spans this long.
        val newest = files.maxOf { it.lastModified() }
        val contents = files
            .filter { it.lastModified() >= newest - RUN_WINDOW_MS }
            .mapNotNull { runCatching { it.readText() }.getOrNull() }
        if (contents.isEmpty()) return

        val table = LoupeResultsFormatter.format(contents, Colors.auto())
        if (table.isNotBlank()) logger.lifecycle(table)
    }

    private companion object {
        /** Anything older than this relative to the newest result is a previous run. */
        const val RUN_WINDOW_MS = 10 * 60 * 1000L
    }
}