package com.wassimbeltaief.loupe.plugin

import com.tschuchort.compiletesting.SourceFile

val composableStub = SourceFile.kotlin(
    "Composable.kt", """
    package androidx.compose.runtime
    annotation class Composable
    """.trimIndent()
)

val loupeRuntimeStub = SourceFile.kotlin(
    "LoupeRuntime.kt", """
    package com.wassimbeltaief.loupe.runtime
    object LoupeRuntime {
        val calls = mutableListOf<Map<String, Any?>>()
        fun record(key: String, file: String, line: Int, params: Array<Pair<String, Any?>>) {
            calls += mapOf("key" to key, "file" to file, "line" to line, "params" to params)
        }
    }
    """.trimIndent()
)
