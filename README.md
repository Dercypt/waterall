# BWD HydroTrack

An Android application designed for Baguio City households navigating rotational water rationing with deterministic countdowns, reserve runout calculations, and crowdsourced tap status verification.

## Tech Stack

- Kotlin
- Android SDK
- Android Jetpack
- ViewBinding
- Kotlin Coroutines
- Gradle
- JUnit

## Features

- Rotational rationing schedule countdown tracking
- Standardized vessel reserve and runout hour calculations
- Projected water deficit and surplus monitoring
- Crowdsourced ground-truth tap status verification
- Offline peer report seeding and deduplication

## Installation

Clone the repository and install dependencies with Gradle:

```bash
git clone https://github.com/Dercypt/waterall.git
cd waterall
./gradlew assembleDebug
```

## Usage

Run the verification harness or execute unit tests:

```bash
./verify.sh
./gradlew testDebugUnitTest
```

## License

MIT
