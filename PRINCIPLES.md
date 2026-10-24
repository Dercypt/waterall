# PRINCIPLES: Governance, Escalations & Defaults

This document governs engineering practices, default patterns, and mandatory escalation triggers for the **BWD HydroTrack** project developed by team **Waterall**.

---

## 1. Architectural Defaults

1. **Language & Toolchain:**
   - **Kotlin** is the default language for all application logic and Android components.
   - Clean separation between pure domain models/calculators and Android UI Framework dependencies. Domain classes must NOT import Android SDK classes (`android.*`) so that invariants can be tested in pure JVM unit tests without mocks.
2. **Architecture:**
   - Model-View-ViewModel (MVVM) with StateFlow / LiveData or clean observer delegates.
   - ViewBinding for zero-reflection, type-safe XML layout binding.
   - Screen View Delegates (`CalendarViewDelegate`, `ReservesViewDelegate`, `ReportViewDelegate`) decouple screen-specific view binding, state observation, and listeners from `MainActivity`, keeping activity controllers lightweight and focused on navigation routing and lifecycle coordination.
3. **Data Immutability:**
   - Domain models are Kotlin `data class` types with `val` properties. State transformations return new immutable copies.

---

## 2. Standard Escalation Triggers

Any developer or agent encountering the following situations must halt, report, and request explicit human review:

| Category | Escalation Trigger | Required Action |
| :--- | :--- | :--- |
| **Dependencies** | Adding any third-party Android library or Gradle plugin | Document license, binary footprint, and justification before adding to `build.gradle.kts`. |
| **Domain Schema** | Modifying container types, zone schedules, or report states | Update `LAWS.md` and ensure all law invariant tests pass before modifying UI. |
| **Public API** | Changing repository contracts, calculation interfaces, or JSON schemas | Stage deprecation or obtain approval from peer developers. |
| **Platform Min SDK**| Bumping `minSdk` above 26 or changing build tools version | Verify device compatibility target for local users and submission rubric. |

---

## 3. Code Standards & Semantic Error Prevention

1. **Calculations:**
   - Never perform division without checking for zero divisor ($R = 0$). Always handle edge conditions gracefully (e.g., return infinite reserve or zero deficit).
2. **Timers & Background Tasks:**
   - Countdown timers must be lifecycle-aware. Stop any running countdown handlers or coroutine loops in `onDestroy()` / `onStop()` to prevent memory leaks and thread crashes.
3. **Time Formatting:**
   - All countdown and schedule timestamps must format as two-digit padded strings (`02:47:13`, `06:00 AM – 10:00 AM`).
