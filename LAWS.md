# LAWS: Waterall Domain Invariants

This document establishes the inviolable laws governing all business calculations, state machines, and data processing within the **BWD HydroTrack** application developed by **Waterall**. No code change, refactor, or test may bypass or relax these invariants without an explicit update to this document.

---

### Law 1: Conservation of Water & Non-Negative Storage
$$\forall t, \quad 0.0 \le \text{currentVolumeLiters}(t) \le \text{totalCapacityLiters}$$

1. **Capacity Clamping:** The current volume of stored water can never exceed total physical storage capacity and can never drop below zero.
2. **Deterministic Runout Time:**
   - If the effective daily consumption rate $R > 0$, the remaining reserve time in hours is strictly given by:
     $$T_{\text{runout}} = \frac{V_{\text{current}}}{R} \times 24.0$$
   - If consumption rate $R = 0$, $T_{\text{runout}} = +\infty$ (`Double.POSITIVE_INFINITY`).
3. **Deficit Non-Negativity:**
   - The projected water deficit before the next BWD supply window ($T_{\text{next}}$ hours away) is:
     $$\text{Deficit} = \max\left(0.0, (T_{\text{next}} - T_{\text{runout}}) \times \frac{R}{24.0}\right)$$
   - A surplus reserve must yield $\text{Deficit} = 0.0$ liters and positive buffer hours. An empty reserve ($V_{\text{current}} = 0.0$) with active consumption ($R > 0.0$) is strictly categorized as depleted before supply ($T_{\text{runout}} \le T_{\text{next}}$), never as a surplus reserve.

---

### Law 2: Rationing Window Chronology & Disjoint Intervals
$$\forall w \in \text{Schedules}, \quad w.\text{startTime} < w.\text{endTime}$$

1. **Strict Positive Duration:** Every scheduled BWD supply window must possess an end time strictly greater than its start time. Zero or negative duration intervals are prohibited.
2. **Temporal Non-Overlap:** Within any given Barangay Zone, no two supply windows may overlap in time.
3. **Monotonic Countdown:** The active countdown timer must strictly count down to `endTime` during active flow, and count down to `startTime` of the nearest future window during dry rationing. Countdown values are strictly non-negative; once expired, the timer must transition deterministically to the next state.

---

### Law 3: Discrete Vessel Quantization
$$\forall c \in \text{Containers}, \quad c.\text{count} \in \mathbb{N}_0 \quad \land \quad c.\text{unitCapacityLiters} > 0.0$$

1. **Standardized Baguio Vessels:** Storage counts must be non-negative whole numbers representing physical vessels:
   - Blue drums: 200 Liters
   - Overhead tanks: 500, 1000, or 2000 Liters
   - Slim / Gallon containers: 20 Liters
2. Total capacity is strictly the dot product:
   $$\text{totalCapacity} = \sum_{i} (\text{count}_i \times \text{unitCapacity}_i)$$

---

### Law 4: Ground-Truth Report Validity & Temporal Integrity
$$\forall r \in \text{Reports}, \quad r.\text{timestamp} \le \text{now}() \quad \land \quad r.\text{status} \in \{\text{RUNNING}, \text{LOW\_PRESSURE}, \text{DRY}\}$$

1. **Closed Enum States:** Crowdsourced status reports must strictly evaluate to one of the three verified physical tap states.
2. **No Future Timestamps:** A status report timestamp cannot exceed current epoch time (`timestamp <= System.currentTimeMillis()`).
3. **Zone Integrity:** Every report must bind to a recognized, active Barangay Zone identifier.
