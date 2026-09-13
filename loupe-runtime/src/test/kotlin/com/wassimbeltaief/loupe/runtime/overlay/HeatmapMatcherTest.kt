package com.wassimbeltaief.loupe.runtime.overlay

import androidx.compose.ui.tooling.data.CallGroup
import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.SourceLocation
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.unit.IntRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(UiToolingDataApi::class)
class HeatmapMatcherTest {

    private fun call(
        name: String?,
        box: IntRect,
        sourceFile: String? = null,
        children: List<Group> = emptyList(),
    ): CallGroup = CallGroup(
        key = name,
        name = name,
        box = box,
        location = sourceFile?.let {
            SourceLocation(lineNumber = 0, offset = 0, length = 0, sourceFile = it, packageHash = 0)
        },
        identity = null,
        parameters = emptyList(),
        data = emptyList(),
        children = children,
        isInline = false,
    )

    private val box = IntRect(0, 0, 100, 50)
    private val emptyBox = IntRect(0, 0, 0, 0)

    @Test
    fun `key uses file prefix when file differs from function`() {
        assertEquals(
            "UnstableListScenario.ProductCard",
            HeatmapMatcher.keyFor("ProductCard", "UnstableListScenario.kt"),
        )
    }

    @Test
    fun `key drops prefix when file matches function`() {
        assertEquals("ProductCard", HeatmapMatcher.keyFor("ProductCard", "ProductCard.kt"))
    }

    @Test
    fun `key is bare name without source file`() {
        assertEquals("ProductCard", HeatmapMatcher.keyFor("ProductCard", null))
    }

    @Test
    fun `key is null for empty name`() {
        assertNull(HeatmapMatcher.keyFor("", "File.kt"))
    }

    @Test
    fun `collect walks nested named groups`() {
        val tree = call(
            name = null,
            box = box,
            children = listOf(
                call(
                    "ProductCard",
                    IntRect(10, 20, 110, 70),
                    "UnstableListScenario.kt",
                    children = listOf(call(null, box)),
                ),
            ),
        )
        val found = HeatmapMatcher.collect(tree)
        assertEquals(1, found.size)
        assertEquals("UnstableListScenario.ProductCard", found[0].key)
        assertEquals(IntRect(10, 20, 110, 70), found[0].rect)
    }

    @Test
    fun `collect skips empty boxes and unnamed groups`() {
        val tree = call(
            name = null,
            box = box,
            children = listOf(
                call("Blank", emptyBox, "File.kt"),
                call(null, box, "File.kt"),
            ),
        )
        assertTrue(HeatmapMatcher.collect(tree).isEmpty())
    }

    @Test
    fun `severity follows config thresholds`() {
        assertEquals(HeatmapSeverity.Hot, HeatmapMatcher.severityFor(16, 16, 4))
        assertEquals(HeatmapSeverity.Warm, HeatmapMatcher.severityFor(4, 16, 4))
        assertEquals(HeatmapSeverity.Healthy, HeatmapMatcher.severityFor(3, 16, 4))
    }
}
