package com.wassimbeltaief.loupe.sample

import android.app.Application
import com.wassimbeltaief.loupe.runtime.LoupeConfig
import com.wassimbeltaief.loupe.runtime.LoupeRuntime

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LoupeRuntime.install(
            application = this,
            config = LoupeConfig(
                hotThreshold = 5,
                warmThreshold = 2,
                windowSeconds = 5,
            ),
        )
    }
}
