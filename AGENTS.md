# AGENTS: Agent & Automation Operational Manual

This operational manual guides autonomous AI agents, human developers, and CI/CD pipelines working within the **Waterall** codebase.

---

## 1. Prime Directive

Whenever you read, modify, or add code to this repository:
1. **Never violate `LAWS.md`:** All calculations (drum volume, depletion hours, countdown time, crowdsourced verification) must satisfy the domain invariants.
2. **Consult `PRINCIPLES.md`:** If an escalation trigger occurs (adding external dependencies, changing the data model, altering public contracts), stop and consult the user.
3. **Execute `HARNESS.md` (`./verify.sh`):** Every code addition or refactoring must pass `./verify.sh` without warnings or failures before concluding work.

---

## 2. Directory Layout & Mental Model

```
waterall-sandbox/
├── LAWS.md               # Non-negotiable domain invariants
├── PRINCIPLES.md         # Architecture defaults & escalation triggers
├── HARNESS.md            # Verification pipeline guide
├── AGENTS.md             # This operational guide
├── verify.sh             # Automated verification runner
├── tests/laws/           # Invariant test specification stubs
│
├── app/
│   ├── build.gradle.kts  # Android application build configuration
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/waterall/
│       │   │   ├── domain/
│       │   │   │   ├── model/         # Pure domain entities (Zone, Vessel, Schedule, Report)
│       │   │   │   └── calculator/    # Runout & Countdown pure calculations
│       │   │   └── ui/                # Activities, Adapters, and View Binding
│       │   └── res/                   # Layouts, strings, drawable assets, colors
│       └── test/kotlin/com/waterall/
│           └── domain/laws/           # Domain Invariant Law unit tests (JVM)
```

---

## 3. Workflow Protocol for Agents

```
[Receive Request]
       │
       ▼
[Check LAWS.md & PRINCIPLES.md] ──(Escalation needed?)──► [Ask User]
       │ (No)
       ▼
[Implement Pure Domain / UI Code]
       │
       ▼
[Run ./verify.sh]
       │
       ├─► (Failures) ──► Fix invariants or compilation errors
       │
       ▼ (Pass)
[Deliver Verified Working Solution]
```
