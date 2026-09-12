package com.wassimbeltaief.loupe.plugin

import com.tschuchort.compiletesting.SourceFile

val composableStub = SourceFile.kotlin(
    "Composable.kt", """
    package androidx.compose.runtime
    annotation class Composable
    """.trimIndent()
)

val loupeIgnoreStub = SourceFile.kotlin(
    "LoupeIgnore.kt", """
    package com.wassimbeltaief.loupe.runtime
    annotation class LoupeIgnore
    """.trimIndent()
)

val lambdaRefStub = SourceFile.kotlin(
    "LambdaRef.kt", """
    package com.wassimbeltaief.loupe.runtime.model
    data class LambdaRef(val identityHashCode: Int)
    """.trimIndent()
)

val loupeRedactStub = SourceFile.kotlin(
    "LoupeRedact.kt", """
    package com.wassimbeltaief.loupe.runtime
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.BINARY)
    annotation class LoupeRedact
    """.trimIndent()
)

val loupeRuntimeStub = SourceFile.kotlin(
    "LoupeRuntime.kt", """
    package com.wassimbeltaief.loupe.runtime
    object LoupeRuntime {
        val calls = mutableListOf<Map<String, Any?>>()
        val endCalls = mutableListOf<String>()
        fun record(key: String, file: String, line: Int, params: Array<Pair<String, Any?>>) {
            calls += mapOf("key" to key, "file" to file, "line" to line, "params" to params)
        }
        fun recordEnd(key: String) {
            endCalls += key
        }
    }
    """.trimIndent()
)
