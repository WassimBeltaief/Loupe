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
        val stateCalls = mutableListOf<List<Any?>>()
        fun record(key: String, file: String, line: Int, params: Array<Pair<String, Any?>>, instance: Int) {
            calls += mapOf("key" to key, "file" to file, "line" to line, "params" to params, "instance" to instance)
        }
        fun recordEnd(key: String, instance: Int) {
            endCalls += key
        }
        fun trackState(key: String, instance: Int, name: String, value: Any?) {
            stateCalls += listOf(key, instance, name, value)
        }
    }
    """.trimIndent()
)

val composeStateStub = SourceFile.kotlin(
    "State.kt", """
    package androidx.compose.runtime
    import kotlin.reflect.KProperty
    interface State<out T> { val value: T }
    interface MutableState<T> : State<T> { override var value: T }
    fun <T> mutableStateOf(value: T): MutableState<T> = object : MutableState<T> {
        override var value: T = value
    }
    fun <T> remember(calculation: () -> T): T = calculation()
    operator fun <T> MutableState<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value
    operator fun <T> MutableState<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
    """.trimIndent()
)
