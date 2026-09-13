# Loupe

**See _why_ your Compose UI recomposes — on device, with one dependency.**

Add a single `debugImplementation`, and Loupe instruments every composable for you: a live overlay ranks the hottest ones, and the drill-down tells you exactly which parameter (or local state) caused each recomposition. No code changes, no Android Studio, no USB.

<table>
  <tr>
    <th align="center" width="50%">Happy path</th>
    <th align="center" width="50%">Unhappy path</th>
  </tr>
  <tr>
    <td align="center" valign="top">
      <img src="LoupeRecording.gif" width="300" alt="Stable album grid: liking one album recomposes only that card"/>
    </td>
    <td align="center" valign="top">
      <img src="LoupeUnhappyPath.gif" width="300" alt="Comment list recomposing every second because of a ticking viewmodel state"/>
    </td>
  </tr>
  <tr>
    <td valign="top">
      Loupe makes it easy to see at a glance when things are good: the screen is healthy and skipping as intended.
    </td>
    <td valign="top">
        Loupe makes it just as easy to see when things are bad: the wasted work stands out instead of hiding.
    </td>
  </tr>
</table>

## What you get

- **Live overlay:** on-screen composables ranked by recomposition count and cost, with proportional bars.
- **Drill-down:** tap a row for a burst-grouped timeline, per-parameter blame (`changed`, `lambda`, `MutableList`, `state`) and 💡 fix suggestions.
- **Local state changes:** `counter 0 → 1` shows when a composable recomposes because of its own `remember { mutableStateOf(...) }`.
- **Share with QA:** export the full history as JSON to Slack/Jira. No developer needed to reproduce the issue.
- **Heatmap (opt-in):** coloured borders and count badges drawn over the composables on screen.

## Install

```kotlin
// app/build.gradle.kts
plugins {
    id("com.wassimbeltaief.loupe") version "1.0.1"
}

loupe {
    packageFilter.set(listOf("com.mycompany.myapp"))
}

dependencies {
    debugImplementation("com.wassimbeltaief:loupe-runtime:1.0.1")
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

## Feedback

Bugs, questions and ideas: [open an issue](https://github.com/WassimBeltaief/Loupe/issues).
