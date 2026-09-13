package com.wassimbeltaief.loupe.runtime.model

/**
 * The result of comparing one parameter between two recompositions.
 *
 * These cases are kept apart because they mean different things. A real value
 * change is not the same as a lambda that was recreated, and an unchanged
 * parameter is not a problem at all.
 */
sealed class ParamVerdict {
    /** First time this composable ran, so there is no previous value to compare. */
    object FirstComposition : ParamVerdict()

    /** The value is the same as last time. */
    object Unchanged : ParamVerdict()

    /** The value is different from last time. */
    object Changed : ParamVerdict()

    /**
     * A function type. Lambdas are compared by identity, because two lambdas with
     * the same behaviour are still different objects. See [LambdaRef].
     */
    object LambdaIdentity : ParamVerdict()
}
