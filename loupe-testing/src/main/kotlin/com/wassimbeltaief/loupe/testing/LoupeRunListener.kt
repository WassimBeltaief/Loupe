package com.wassimbeltaief.loupe.testing

import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/**
 * Prints one compact Loupe report at the end of an instrumentation run.
 *
 * It runs in CI and on device. Because it is a JUnit [RunListener], the AndroidX
 * runner discovers it through
 * `META-INF/services/org.junit.runner.notification.RunListener` — no runner
 * configuration and no per-test wiring are required.
 *
 * The report is emitted on every run (pass or fail) and goes to `System.out`,
 * which Gradle surfaces in CI and Android surfaces as `I/System.out` in Logcat.
 */
class LoupeRunListener : RunListener() {

    private var started = 0
    private var finished = 0
    private var ignored = 0
    private val failures = mutableListOf<LoupeTestReportPrinter.Failure>()
    private var runStartMs = 0L

    override fun testRunStarted(description: Description?) {
        runStartMs = System.currentTimeMillis()
    }

    override fun testStarted(description: Description?) {
        started++
    }

    override fun testIgnored(description: Description?) {
        ignored++
    }

    override fun testFinished(description: Description?) {
        finished++
    }

    override fun testFailure(failure: Failure?) {
        val error = failure?.exception
        failures += if (error is LoupeAssertionError) {
            LoupeTestReportPrinter.Failure(
                testName = testName(failure.description),
                expected = error.expected,
                actual = error.actual,
                composableKey = error.composableKey,
                blamedParams = error.blamedParams,
                totalCompositions = error.totalCompositions,
                windowRecompositions = error.windowRecompositions,
                totalDurationMs = error.totalDurationMs,
                hotThreshold = error.hotThreshold,
                warmThreshold = error.warmThreshold,
            )
        } else {
            LoupeTestReportPrinter.Failure(
                testName = testName(failure?.description),
                expected = null,
                actual = error?.message?.lineSequence()?.firstOrNull(),
                composableKey = null,
                blamedParams = emptyList(),
                totalCompositions = 0,
                windowRecompositions = 0,
                totalDurationMs = 0f,
            )
        }
    }

    override fun testRunFinished(result: Result?) {
        val attempted = finished.coerceAtLeast(started)
        val failed = failures.size
        println(
            LoupeTestReportPrinter.format(
                LoupeTestReportPrinter.RunReport(
                    total = attempted + ignored,
                    passed = (attempted - failed).coerceAtLeast(0),
                    failed = failed,
                    skipped = ignored,
                    durationMs = if (runStartMs == 0L) 0L else System.currentTimeMillis() - runStartMs,
                    failures = failures,
                ),
            ),
        )
    }

    private fun testName(description: Description?): String {
        if (description == null) return "unknown"
        val method = description.methodName
        val className = description.className?.substringAfterLast('.')
        return if (method != null) "$className#$method" else description.displayName
    }
}
