package com.wassimbeltaief.loupe.runtime.model

/** Wraps a function value as its identity hash, because lambdas are never equal by value. */
data class LambdaRef(val identityHashCode: Int)
