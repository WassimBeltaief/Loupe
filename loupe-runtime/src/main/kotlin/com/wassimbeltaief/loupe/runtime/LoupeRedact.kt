package com.wassimbeltaief.loupe.runtime

/**
 * Marks a parameter type whose values must never be captured.
 *
 * The compiler plugin replaces the value with "[redacted]" in the injected
 * `record()` call, so it never reaches the registry. Use it for tokens,
 * sessions, credentials and other personal data.
 *
 * It keeps `BINARY` retention, because the compiler plugin reads it from the IR.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class LoupeRedact
