package com.wassimbeltaief.loupe.gradle

import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Turns AGP's connected-test result XML into the Loupe summary table.
 *
 * It is a pure object with no Gradle types, so it can be unit-tested on the JVM.
 * The structured data comes from the `[loupe]` marker line that
 * `LoupeAssertionError` appends to its message, which AGP copies verbatim into
 * the `<failure>` text.
 *
 * Results from several XML files (one per device) are merged by test identity,
 * keeping the worst outcome. A test that fails on any device is reported once
 * as failed, so the table shows "7 tests", not "28 tests on 4 devices".
 */
internal object LoupeResultsFormatter {

    private const val W_COMPOSABLE = 22
    private const val W_LEVEL = 5   // "Level" / "WARM " / "HOT  "
    private const val W_COUNT = 6   // "Count " / "  100×"
    private const val W_COST = 9    // "Cost     " / " 1234.5ms"
    private const val W_BLAME = 18

    fun format(xmlContents: List<String>, colors: Colors): String {
        val suites = xmlContents.flatMap { parseSuites(it) }
        if (suites.isEmpty()) return ""

        val merged = LinkedHashMap<String, TestCase>()
        for (suite in suites) {
            for (testCase in suite.testCases) {
                val key = "${testCase.className}#${testCase.methodName}"
                val existing = merged[key]
                merged[key] = if (existing == null || (!existing.failed && testCase.failed)) testCase else existing
            }
        }
        if (merged.isEmpty()) return ""

        val failures = merged.values.filter { it.failed }
        val skipped = merged.values.count { it.skipped }
        val passed = (merged.size - failures.size - skipped).coerceAtLeast(0)
        val seconds = suites.groupBy { it.name }
            .values
            .sumOf { group -> group.maxOf { it.timeSeconds } }
        val timer = "${"%.1f".format(Locale.US, seconds)}s"

        return buildString {
            // ── line 1: summary ──────────────────────────────────────────────
            val passDot = colors.green("●")
            if (failures.isEmpty() && skipped == 0) {
                appendLine("  $passDot  ${colors.green("${merged.size} tests passed")}   ($timer)")
            } else {
                val parts = mutableListOf<String>()
                parts += "$passDot ${colors.green("$passed passed")}"
                if (failures.isNotEmpty()) parts += "${colors.red("●")} ${colors.red("${failures.size} failed")}"
                if (skipped > 0) parts += "$skipped skipped"
                parts += "${merged.size} tests"
                parts += timer
                appendLine("  " + parts.joinToString("  ·  "))
            }

            // ── single merged table, HOT first then WARM, then by count desc ─
            val rows = failures
                .mapNotNull { tc -> tc.marker?.let { m -> tc to m } }
                .sortedWith(
                    compareBy<Pair<TestCase, Marker>> { statusOrder(it.second.status) }
                        .thenByDescending { it.second.total }
                )
            if (rows.isNotEmpty()) {
                appendLine()
                append(mergedTable(rows, colors))
            }
        }.trimEnd()
    }

    private fun statusOrder(status: String) = when (status) {
        "HOT" -> 0
        "WARM" -> 1
        else -> 2
    }

    private fun mergedTable(rows: List<Pair<TestCase, Marker>>, colors: Colors): String = buildString {
        val wc = W_COMPOSABLE; val wl = W_LEVEL; val wn = W_COUNT; val ws = W_COST; val wb = W_BLAME
        fun h(w: Int) = "─".repeat(w + 2)

        appendLine("  ┌${h(wc)}┬${h(wl)}┬${h(wn)}┬${h(ws)}┬${h(wb)}┐")
        appendLine("  │ ${"Composable".padEnd(wc)} │ ${"Level".padEnd(wl)} │ ${"Count".padEnd(wn)} │ ${"Cost".padEnd(ws)} │ ${"Blame".padEnd(wb)} │")
        appendLine("  ├${h(wc)}┼${h(wl)}┼${h(wn)}┼${h(ws)}┼${h(wb)}┤")
        rows.forEach { (_, marker) ->
            val (dotStr, levelCell) = when (marker.status) {
                "HOT" -> colors.red("●") to colors.red("HOT".padEnd(wl))
                "WARM" -> colors.yellow("●") to colors.yellow("WARM".padEnd(wl))
                else -> colors.green("●") to "OK".padEnd(wl)
            }
            val composableCell = dotStr + " " + marker.composable.truncate(wc - 2).padEnd(wc - 2)
            val countCell = "${marker.total}×".padStart(wn)
            val costCell = "${marker.durationMs}ms".padStart(ws)
            val blameCell = prettyBlame(marker.blame).truncate(wb).padEnd(wb)
            appendLine("  │ $composableCell │ $levelCell │ $countCell │ $costCell │ $blameCell │")
        }
        append("  └${h(wc)}┴${h(wl)}┴${h(wn)}┴${h(ws)}┴${h(wb)}┘")
    }

    private fun prettyBlame(raw: String): String {
        if (raw == "-" || raw.isBlank()) return "—"
        return raw.split(",").joinToString(", ") { part ->
            "${part.substringBefore(':')} (${part.substringAfter(':', "0")}×)"
        }
    }

    private fun String.truncate(max: Int): String =
        if (length <= max) this else substring(0, max - 1) + "…"

    // ── XML parsing ──────────────────────────────────────────────────────────

    private data class ParsedSuite(
        val name: String,
        val timeSeconds: Double,
        val testCases: List<TestCase>,
    )

    private data class TestCase(
        val className: String,
        val methodName: String,
        val failed: Boolean,
        val skipped: Boolean,
        val reason: String?,
        val marker: Marker?,
    )

    private data class Marker(
        val composable: String,
        val status: String,
        val blame: String,
        val total: Int,
        val durationMs: String,
    )

    private fun parseSuites(xml: String): List<ParsedSuite> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // No external entities — the XML only ever contains test output.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())

        val suiteNodes = doc.getElementsByTagName("testsuite")
        (0 until suiteNodes.length).mapNotNull { i ->
            val suite = suiteNodes.item(i) as? Element ?: return@mapNotNull null
            val testcaseNodes = suite.getElementsByTagName("testcase")
            val testCases = (0 until testcaseNodes.length).mapNotNull { j ->
                parseTestCase(testcaseNodes.item(j) as? Element ?: return@mapNotNull null)
            }
            ParsedSuite(
                name = suite.getAttribute("name"),
                timeSeconds = suite.getAttribute("time").toDoubleOrNull() ?: 0.0,
                testCases = testCases,
            )
        }
    }.getOrDefault(emptyList())

    private fun parseTestCase(testcase: Element): TestCase? {
        val className = testcase.getAttribute("classname").substringAfterLast('.')
        val methodName = testcase.getAttribute("name")
        if (methodName.isBlank()) return null

        val failureNodes = testcase.getElementsByTagName("failure")
        if (failureNodes.length == 0) {
            val skipped = testcase.getElementsByTagName("skipped").length > 0
            return TestCase(className, methodName, failed = false, skipped = skipped, reason = null, marker = null)
        }

        val text = failureNodes.item(0).textContent.orEmpty()
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val firstLine = lines.firstOrNull().orEmpty()
        // "com.foo.LoupeAssertionError: message" → "message"
        val reason = firstLine.substringAfter(": ", firstLine).ifBlank { firstLine }
        val markerLine = lines.firstOrNull { it.startsWith("[loupe] ") }
        return TestCase(
            className = className,
            methodName = methodName,
            failed = true,
            skipped = false,
            reason = reason,
            marker = markerLine?.let { parseMarker(it.removePrefix("[loupe] ")) },
        )
    }

    private fun parseMarker(payload: String): Marker? {
        val values = payload.split(" ")
            .mapNotNull { token ->
                val idx = token.indexOf('=')
                if (idx <= 0) null else token.substring(0, idx) to token.substring(idx + 1)
            }
            .toMap()
        val composable = values["composable"] ?: return null
        return Marker(
            composable = composable,
            status = values["status"] ?: "OK",
            blame = values["blame"] ?: "-",
            total = values["total"]?.toIntOrNull() ?: 0,
            durationMs = values["durationMs"] ?: "0.0",
        )
    }
}
