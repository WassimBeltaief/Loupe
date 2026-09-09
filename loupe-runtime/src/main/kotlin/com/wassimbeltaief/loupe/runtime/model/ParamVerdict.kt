package com.wassimbeltaief.loupe.runtime.model

sealed class ParamVerdict {
    object FirstComposition : ParamVerdict()
    object Unchanged : ParamVerdict()
    object Changed : ParamVerdict()
    object LambdaIdentity : ParamVerdict()
}
