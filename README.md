# SIGNAL YARD

**Every train has somewhere to be. Get them there.**

SIGNAL YARD is an offline-first, native Android railway dispatching and network strategy game built with Kotlin, Jetpack Compose, Material 3, and a custom Compose Canvas 2D miniature railway diorama renderer.

## Features Implemented (Phases 0–3 Foundation)

- **Core Railway Simulation Engine (`com.example.sim`)**
  - Graph-based railway topology (`TrackGraph`, `TrackNode`, `TrackSegment`) with arc-length parameterized Bezier curves (`TrackCurve`).
  - Mechanically interlocked switches (`TrackSwitch`) with occupancy and route-reservation locking.
  - Block signal aspect calculation (`GREEN`, `YELLOW`, `RED`) with manual dispatcher Hold/Release control and human-readable status explanations.
  - Exact path-history carriage following (`TraversedSegment`, `CarriagePose`) and multi-segment occupancy for long heavy freight consists.
  - 5 mechanically distinct train categories (`LOCAL_PASSENGER`, `EXPRESS_PASSENGER`, `FREIGHT`, `HEAVY_FREIGHT`, `MAINTENANCE`) with unique speeds, acceleration, braking distances, and infrastructure compatibility.
  - Route planning (`RoutePlanner`) and deadlock / off-route misalignment detection (`InterlockingSystem`).
- **Playable Modes**
  - **Signal School (Tutorial)**: Guided vertical-slice introduction to switches, block signals, train inspection, and platform routing.
  - **Quick Dispatch**: 4 handcrafted 2–5 minute challenges (*Countryside Crossing*, *Express Priority*, *Foundry Yard Shunt*, *Twin Viaduct Interlocking*).
  - **Rush Hour**: 5m, 10m, and 15m escalating real-time operations on the *Alderford Central* 3-platform network with streak multipliers and safety scoring.
  - **Zen Dispatch**: Relaxed free-play miniature railway diorama with on-demand train dispatching and adjustable simulation speeds (`0.5x`–`3x`).
- **Offline Local Persistence & Audio**
  - Room database (`SignalYardDatabase`) storing scenario medals, personal bests, streaks, and operating shift history.
  - DataStore Preferences (`SettingsRepository`) for audio, colorblind signal symbols, and route guidance.
  - Procedural offline PCM sound synthesizer (`SoundManager`) with zero external network dependencies.

## Build & Test

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
```

Debug APKs are also built automatically via `.github/workflows/build-apk.yml`.
