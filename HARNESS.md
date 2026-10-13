# HARNESS: Verification Pipeline

This document defines the automated verification pipeline for the **BWD HydroTrack** codebase (developed by team **Waterall**).

---

## Verification Pipeline Stages

Every commit, pull request, or agent change must pass the full verification harness defined in `./verify.sh`:

1. **Static Analysis & File Integrity:**
   - Verify all required governance documents exist (`LAWS.md`, `PRINCIPLES.md`, `HARNESS.md`, `AGENTS.md`).
   - Validate that Android manifest, Gradle configuration, and source sets exist.
2. **Domain Law Invariant Unit Tests:**
   - Run pure Kotlin JVM unit tests in `com.waterall.domain.laws.*`.
   - Assert all 4 domain invariants (Conservation of Water, Rationing Window Chronology, Container Quantization, Report Validity).
3. **Android Compilation & Unit Test Suite:**
   - Run Gradle unit test target: `./gradlew testDebugUnitTest`.
4. **Android Linting (Optional in local fast-lane, mandatory in CI):**
   - Run `./gradlew lintDebug` or verify zero unhandled exceptions.

---

## Quick Reference Commands

```bash
# Full verification pipeline
./verify.sh

# Run only domain law tests
./gradlew testDebugUnitTest --tests "com.waterall.domain.laws.*"

# Assemble Android APK for face-to-face / demo recording
./gradlew assembleDebug
```
