# BWD HydroTrack

> An Android application by **Waterall** designed for Baguio City households navigating Baguio Water District (BWD) rotational rationing schedules.

**BWD HydroTrack** provides deterministic schedule countdowns, household reserve and runout buffer calculations across discrete vessel types (e.g., 200L blue drums, overhead tanks, 20L containers), and crowdsourced ground-truth tap status reporting.

---

## Problem Domain

Rotational water rationing leaves Baguio residents uncertain about supply window timing and remaining reserves. BWD HydroTrack models and enforces:
- **Conservation of Water & Deficit Calculation:** Quantifies current reserves, daily consumption rates, runout hours ($T_{\text{runout}}$), and projected deficit before the next supply window.
- **Rationing Window Chronology:** Tracks non-overlapping barangay supply windows with lifecycle-aware monotonic countdown timers.
- **Discrete Vessel Quantization:** Standardized capacity tracking across physical containers (blue drums, overhead tanks, slim/gallon containers).
- **Crowdsourced Tap Verification:** Real-time ground-truth tap status reports (`RUNNING`, `LOW_PRESSURE`, `DRY`) bound to active barangay zones, with built-in multi-report peer seeding (`InMemoryReportRepository.createSeeded`) and a "Load sample community reports" toggle for live demonstration when offline.

Domain invariants and engineering principles are specified in [LAWS.md](LAWS.md) and [PRINCIPLES.md](PRINCIPLES.md).

---

## Prerequisites & Environment

Ensure your development environment meets the following specifications:

- **JDK:** OpenJDK 17 or 21 (`sourceCompatibility = JavaVersion.VERSION_17`, Gradle runtime tested on JDK 17 and JDK 21).
- **Android SDK:**
  - `compileSdk`: 35
  - `targetSdk`: 35
  - `minSdk`: 26 (Android 8.0 Oreo)
- **Environment Variables:** Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` to your Android SDK location, or specify `sdk.dir` in `local.properties`.

---

## Execution & Build Commands

### Verification Pipeline
Run the automated verification pipeline to validate governance documents, SDK environment, and unit test suites:
```bash
./verify.sh
```

### Build Debug APK
Assemble the Android debug APK:
```bash
./gradlew assembleDebug
```

### Run Unit Tests
Run all unit tests:
```bash
./gradlew testDebugUnitTest
```

Run domain law invariant tests only:
```bash
./gradlew testDebugUnitTest --tests "com.waterall.domain.laws.*"
```

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.