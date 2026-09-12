package com.wassimbeltaief.loupe.runtime

/**
 * Marks a parameter TYPE whose values must never be captured by Loupe.
 * The compiler plugin replaces the captured value with "[redacted]" in the
 * injected record() call — use for tokens, sessions, credentials, PII (#20).
 *
 * BINARY retention: the annotation must survive into IR, where the plugin reads it.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class LoupeRedact
