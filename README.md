# Loupe

A zero-instrumentation recomposition debugger for Jetpack Compose.

Add one `debugImplementation` dependency and get a live on-device overlay that tracks every composable on screen, shows how many times it recomposed, and tells you exactly which parameter caused it — no Android Studio, no USB cable, no code changes required.

<br/>

![Loupe demo](Loupe_recording.gif)

<br/>

## Installation

```kotlin
// build.gradle.kts (app module)
plugins {
    id("com.wassimbeltaief.loupe") version "1.0.0-alpha01"
}

loupe {
    packageFilter = listOf("com.mycompany.myapp")
}

dependencies {
    debugImplementation("com.wassimbeltaief:loupe-runtime:1.0.0-alpha01")
}
```

Then initialise in your `Application`:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LoupeRuntime.install(this)
    }
}
```

That's it. The overlay appears automatically on debug builds.

<br/>

## Coming features

| Feature | Status |
|---|---|
| Drill-down panel — full recomposition timeline per composable | Planned |
| Parameter blame bar — ranked by recomposition contribution | Planned |
| Share report — export history as JSON via the share sheet | Planned |
| `@LoupeRedact` — redact PII parameters from capture | Planned |
| CI testing API — `assertMaxRecompositions()`, `assertStable()` | Planned |
| Logcat output mode | Planned |
| Forced recomposition detection (`wasForced`) | Planned |
| Heatmap borders drawn over on-screen composables | Planned |
