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
                // Bands: 1 green · 3–9 amber · 10+ red
                hotThreshold = 10,
                warmThreshold = 3,
                windowSeconds = 5,
            ),
        )
    }
}
