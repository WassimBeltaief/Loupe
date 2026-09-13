package com.wassimbeltaief.loupe.testing

/**
 * Describes how many total compositions (initial + recompositions) are acceptable.
 *
 * The count is inclusive of the initial composition:
 * - `times(1)` — composed exactly once, never recomposed
 * - `times(2)` — composed once then recomposed once
 * - `atMost(3)` — initial composition plus at most 2 recompositions
 *
 * Create instances with the top-level factory functions [times], [atMost], [atLeast].
 */
sealed class CompositionMatcher {

    internal abstract fun matches(count: Int): Boolean
    internal abstract fun describe(): String

    data class Exactly(val count: Int) : CompositionMatcher() {
        override fun matches(count: Int) = count == this.count
        override fun describe() = "exactly $count composition(s)"
    }

    data class AtMost(val count: Int) : CompositionMatcher() {
        override fun matches(count: Int) = count <= this.count
        override fun describe() = "at most $count composition(s)"
    }

    data class AtLeast(val count: Int) : CompositionMatcher() {
        override fun matches(count: Int) = count >= this.count
        override fun describe() = "at least $count composition(s)"
    }
}

/** Asserts that the composable was composed exactly [n] times (initial + recompositions). */
fun times(n: Int): CompositionMatcher = CompositionMatcher.Exactly(n)

/** Asserts that the total composition count is no more than [n]. */
fun atMost(n: Int): CompositionMatcher = CompositionMatcher.AtMost(n)

/** Asserts that the total composition count is at least [n]. */
fun atLeast(n: Int): CompositionMatcher = CompositionMatcher.AtLeast(n)
