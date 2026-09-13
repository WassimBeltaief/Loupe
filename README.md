# Loupe

**See _why_ your Compose UI recomposes — on device, with one dependency.**

Add a single `debugImplementation`, and Loupe instruments every composable for you: a live overlay ranks the hottest ones, and the drill-down tells you exactly which parameter (or local state) caused each recomposition. No code changes, no Android Studio, no USB.

<p align="center">
  <img src="LoupeRecording.gif" width="300" alt="Loupe overlay and drill-down in action"/>
</p>

## What you get

- **Live overlay** — on-screen composables ranked by recomposition count and cost, with proportional bars.
- **Drill-down** — tap a row for a burst-grouped timeline, per-parameter blame (`changed`, `lambda`, `MutableList`, `state`) and 💡 fix suggestions.
- **Local state changes** — `counter 0 → 1` shows when a composable recomposes because of its own `remember { mutableStateOf(...) }`.
- **Share with QA** — export the full history as JSON to Slack/Jira. No developer needed to reproduce the issue.
- **Heatmap (opt-in)** — coloured borders and count badges drawn over the composables on screen.

## Install

```kotlin
// app/build.gradle.kts
plugins {
    id("com.wassimbeltaief.loupe") version "1.0.0-alpha01"
}

loupe {
    packageFilter.set(listOf("com.mycompany.myapp"))
}

dependencies {
    debugImplementation("com.wassimbeltaief:loupe-runtime:1.0.0-alpha01")
}
```

```kotlin
// Application
override fun onCreate() {
    super.onCreate()
    LoupeRuntime.install(this)
}
```

The compiler plugin only touches **debug builds** — release artifacts are untouched. The overlay needs the `SYSTEM_ALERT_WINDOW` permission; Logcat prints the exact `adb` command if it is missing.

Heatmap borders are the only opt-in part — wrap your content once:

```kotlin
LoupeHeatmapHost { App() }
```

## Documentation

Architecture, configuration and the CI testing API live in [CLAUDE.md](CLAUDE.md). Bugs and ideas: [open an issue](https://github.com/WassimBeltaief/Loupe/issues).
