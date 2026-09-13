package com.wassimbeltaief.loupe.runtime

/**
 * Marks a `@Composable` that Loupe should never record.
 *
 * Use it for composables that recompose by design and would only add noise,
 * for example a live clock or an animation helper.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class LoupeIgnore
