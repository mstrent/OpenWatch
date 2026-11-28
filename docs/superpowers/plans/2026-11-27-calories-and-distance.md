# All-day Calories and Distance Implementation Plan

> **Dropped 2026-11-27, not built.** Upstream derives distance in OpenVitals with a user-entered
> stride; Matt wants no fork to maintain. See the Decisions log in `docs/notes/pixel-watch-status.md`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Write all-day active calories (heart rate or cadence), total calories and distance to Health Connect from the watch's existing heart rate and steps plus OpenWatch's body profile, and show today's calories and distance as watch-face complications.

**Architecture:** Everything is computed on the phone during the existing watch sync. Pure formulas go in `:health-calculations`; pure record builders in `:devices:wearos` next to `WearOsStepsImport`; the sync service gathers this sync's step minutes and per-minute heart rate and writes the records. The watch gains two complications fed by the phone's daily figures (`PUT_DAY`), which grow one field (total kcal).

**Tech Stack:** Kotlin, Android Health Connect client (`androidx.health.connect`), Wear OS complications (`SuspendingComplicationDataSourceService`), JUnit/kotlin-test (existing test setup per module).

**Spec:** `docs/superpowers/specs/2026-11-27-calories-and-distance-design.md`

## Global Constraints

- No Health Services, no vendor library, no new dependency in any release build.
- No new setting or toggle; no second profile. Inputs: weight (Health Connect via `BodyProfileRepository.resolve()`), height/age/sex (`BodyProfile`).
- No population defaults for a missing weight or height: no weight → no calories of any kind; no height → no distance.
- Exactly one OpenWatch record per metric per interval, keyed by a stable `saltedRecordId(prefix + epochMillis, salt)`; a re-sync replaces, never duplicates.
- Minutes inside any recorded exercise session (`readExerciseSessionRanges`) get no all-day active calories and no all-day distance (the workout writes its own).
- Coexistence with Google Health is Health Connect's app priority; no OpenWatch logic for it.
- Watch keeps no network permission; `wearlink` line formats stay backward compatible (lines may only grow at their end).
- Gates before any push: `./gradlew verifyCi verifyWearCi` and `python3 scripts/sync-wiki.py --repo mstrent/OpenWatch --check` (export `ANDROID_HOME=$HOME/Android/Sdk`).

## Decisions this plan makes (spec left them to the builder)

- **"Heart rate raised by effort"** = the minute's mean heart rate inside Keytel's existing gate, `KeytelHeartRate.MinFractionOfMax`..`MaxFractionOfMax` (0.57–0.90) of the max heart rate (`profile.maxHeartRateBpm` or `ageMaxHeartRate(age, sex)`), with at least **3** samples in the minute. Otherwise cadence (`WalkingEnergy`). Without an age, cadence only.
- **Stride** = height × factor, factor `0.414` (mean; `0.415` male, `0.413` female) at ≤ 100 steps/min, rising linearly to `0.65` at 170 steps/min, flat above. Constants named and tunable; validated against Health Services' daily distance on the device (Task 6).
- **Total calories** are written as one `TotalCaloriesBurnedRecord` **per clock hour** touched by this sync's data: resting kcal (Mifflin-St Jeor, needs weight + height + age; else no total) for the covered minutes + OpenWatch's own active kcal in that hour (all-day `wearos_walk_kcal_` records and workout `wearos_workout_energy_` records). Writing totals for every covered hour makes Health Connect's own derivation question moot (spec "Open 1"). The current hour ends at the newest data minute.
- **Versions:** active-calorie and hourly-total records use `clientRecordVersion = clock().toEpochMilli()` (heart rate can change a minute after its steps were written); distance keeps `steps` as version (it depends on steps only).
- **Calories complication shows total kcal**; the existing Active-calories tile is unchanged.

## Review Focus

1. A minute with steps written in an earlier sync whose heart rate arrives in this sync: the heart-rate value must replace the cadence value, never add a second record — Task 2 test `heart rate minute replaces the cadence record of the same minute`.
2. A cycling or strength minute with zero steps but raised heart rate must still get active calories — Task 1 test `raised heart rate without steps costs energy`, Task 3 test `a heart-rate-only minute writes active calories`.
3. A day crossing DST: hour records must use each instant's own offset — Task 2 test `hour records carry the offset in force`.
4. An older watch app that sends a 10-field `PUT_DAY`, and an older phone reading an 11-field line: both parse — Task 4 tests.
5. Missing height must skip distance and totals without failing the sync or the steps — Task 3 test `no height writes steps and active calories but no distance or total`.

---

### Task 1: Per-minute energy and stride formulas

**Files:**
- Create: `health-calculations/src/main/kotlin/tech/mmarca/healthcalculations/insights/AllDayEnergy.kt`
- Create: `health-calculations/src/main/kotlin/tech/mmarca/healthcalculations/insights/StrideDistance.kt`
- Modify: `health-calculations/src/main/kotlin/tech/mmarca/healthcalculations/insights/WorkoutEnergy.kt` (make the private `restingKcal` public, unchanged body)
- Test: `health-calculations/src/test/kotlin/tech/mmarca/healthcalculations/insights/AllDayEnergyTest.kt`, `StrideDistanceTest.kt`

**Interfaces:**
- Consumes: `WalkingEnergy.activeKcalPerMinute(steps, weightKg)`, `KeytelHeartRate`, `ageMaxHeartRate`, `BasalMetabolicRate.mifflinStJeor`, `BodyProfile`.
- Produces:
  - `fun restingKcal(profile: BodyProfile, weightKg: Double, durationMinutes: Double, today: LocalDate): Double` (now public, WorkoutEnergy.kt)
  - `object AllDayEnergy { const val MIN_HEART_RATE_SAMPLES = 3; fun activeKcalPerMinute(steps: Int, meanHeartRateBpm: Double?, heartRateSamples: Int, profile: BodyProfile, weightKg: Double, today: LocalDate): Double; fun basalKcal(profile: BodyProfile, weightKg: Double, minutes: Double, today: LocalDate): Double? }` — `basalKcal` is null unless height and age are known (no 1-MET fallback here).
  - `object StrideDistance { fun strideMetres(heightCm: Double, sex: BiologicalSex?, stepsPerMinute: Int): Double; fun metres(steps: Int, heightCm: Double, sex: BiologicalSex?): Double }`

- [ ] **Step 1: Write the failing tests**

```kotlin
// AllDayEnergyTest
private val man = BodyProfile(birthYear = 1980, weightKg = 80.0, heightCm = 180.0, sex = BiologicalSex.MALE)
private val today = LocalDate.of(2026, 11, 27)

@Test fun `cadence only below the heart rate gate`() =
    assertEquals(WalkingEnergy.activeKcalPerMinute(110, 80.0), AllDayEnergy.activeKcalPerMinute(110, 70.0, 6, man, 80.0, today), 1e-9)
@Test fun `raised heart rate uses Keytel net of rest`() {
    val gross = KeytelHeartRate.kilojoulesPerMinute(BiologicalSex.MALE, 140.0, 80.0, 46) / 4.184
    val expected = gross - restingKcal(man, 80.0, 1.0, today)
    assertEquals(expected, AllDayEnergy.activeKcalPerMinute(20, 140.0, 6, man, 80.0, today), 1e-9)
}
@Test fun `raised heart rate without steps costs energy`() = assertTrue(AllDayEnergy.activeKcalPerMinute(0, 140.0, 6, man, 80.0, today) > 0.0)
@Test fun `too few samples falls back to cadence`() = assertEquals(0.0, AllDayEnergy.activeKcalPerMinute(0, 140.0, 2, man, 80.0, today))
@Test fun `no age means cadence only`() = assertEquals(0.0, AllDayEnergy.activeKcalPerMinute(0, 140.0, 6, man.copy(birthYear = null), 80.0, today))
@Test fun `basal needs height and age`() {
    assertNull(AllDayEnergy.basalKcal(man.copy(heightCm = null), 80.0, 60.0, today))
    assertEquals(BasalMetabolicRate.mifflinStJeor(BiologicalSex.MALE, 80.0, 180.0, 46) / 24.0, AllDayEnergy.basalKcal(man, 80.0, 60.0, today)!!, 1e-9)
}

// StrideDistanceTest
@Test fun `walking stride is height times 0_414 without sex`() = assertEquals(1.80 * 0.414, StrideDistance.strideMetres(180.0, null, 100), 1e-9)
@Test fun `stride rises linearly to 0_65 at 170`() {
    assertEquals(1.80 * 0.65, StrideDistance.strideMetres(180.0, null, 170), 1e-9)
    assertEquals(1.80 * 0.65, StrideDistance.strideMetres(180.0, null, 200), 1e-9)
    assertEquals(1.80 * (0.414 + (0.65 - 0.414) / 2), StrideDistance.strideMetres(180.0, null, 135), 1e-9)
}
@Test fun `sex picks the walking factor`() = assertEquals(1.80 * 0.415, StrideDistance.strideMetres(180.0, BiologicalSex.MALE, 90), 1e-9)
@Test fun `metres is steps times stride at that cadence`() = assertEquals(100 * StrideDistance.strideMetres(170.0, null, 100), StrideDistance.metres(100, 170.0, null), 1e-9)
@Test fun `zero steps is zero metres`() = assertEquals(0.0, StrideDistance.metres(0, 170.0, null))
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :health-calculations:test --tests '*AllDayEnergyTest' --tests '*StrideDistanceTest'`
Expected: FAIL — unresolved references `AllDayEnergy`, `StrideDistance`, `restingKcal`.

- [ ] **Step 3: Implement** `AllDayEnergy`, `StrideDistance`, and make `restingKcal` public. In `activeKcalPerMinute`: Keytel when `heartRateSamples >= MIN_HEART_RATE_SAMPLES`, age known and mean HR within `KeytelHeartRate` fraction of max; result `max(0, gross − restingKcal(…, 1.0, …))`; else `WalkingEnergy.activeKcalPerMinute(steps, weightKg)`. `metres(steps, …)` treats `steps` as one minute's count, so its cadence is `steps`. KDoc cites Keytel 2005 and states the stride factors as this project's own estimate.

- [ ] **Step 4: Run to verify they pass** (same command) — Expected: PASS; also `./gradlew :health-calculations:test` stays green (WorkoutEnergyTest unchanged).

- [ ] **Step 5: Commit** — `feat(health-calculations): all-day energy and stride formulas`

---

### Task 2: Health Connect record builders

**Files:**
- Create: `devices/wearos/src/main/kotlin/tech/mmarca/openwatch/devices/wearos/WearOsDayEnergyImport.kt`
- Modify: `devices/wearos/src/main/kotlin/tech/mmarca/openwatch/devices/wearos/WearOsStepsImport.kt` (remove `activeCaloriesRecords`; keep `ACTIVE_CALORIES_CLIENT_ID_PREFIX` — the new builder reuses it so existing records are replaced, not duplicated)
- Test: `devices/wearos/src/test/kotlin/tech/mmarca/openwatch/devices/wearos/WearOsDayEnergyImportTest.kt` (move the active-calorie cases from `WearOsStepsImportTest`)

**Interfaces:**
- Consumes: Task 1; `WearOsStepMinute(start: Instant, steps: Int)`; `saltedRecordId`.
- Produces:
  - `data class WearOsEnergyMinute(val start: Instant, val steps: Int, val meanHeartRateBpm: Double?, val heartRateSamples: Int)`
  - `object WearOsDayEnergyImport {`
    - `const val DISTANCE_PREFIX = "wearos_walk_distance_"; const val TOTAL_PREFIX = "wearos_total_kcal_"`
    - `fun activeCaloriesRecords(minutes: List<WearOsEnergyMinute>, profile: BodyProfile, weightKg: Double, zone: ZoneId, version: Long, today: LocalDate, salt: String = ""): List<ActiveCaloriesBurnedRecord>` — id `ACTIVE_CALORIES_CLIENT_ID_PREFIX + start`, `Metadata.manualEntry(Device(TYPE_PHONE))` as today; zero-kcal minutes write nothing.
    - `fun distanceRecords(minutes: List<WearOsStepMinute>, heightCm: Double, sex: BiologicalSex?, zone: ZoneId, salt: String = ""): List<DistanceRecord>` — version = steps; zero-step minutes write nothing.
    - `fun hourlyTotalRecords(hours: List<HourEnergy>, zone: ZoneId, version: Long, salt: String = ""): List<TotalCaloriesBurnedRecord>`
    - `data class HourEnergy(val start: Instant, val end: Instant, val basalKcal: Double, val activeKcal: Double)`
    - `fun isOwnActiveOrWorkoutEnergyId(clientRecordId: String?): Boolean` — true for ids starting with `ACTIVE_CALORIES_CLIENT_ID_PREFIX` or `WearOsWorkoutImport.ENERGY_PREFIX`.
    - `fun minutesFromHeartRate(samples: List<Pair<Instant, Long>>): Map<Instant, Pair<Double, Int>>` — mean bpm and count per minute start (truncated to the minute). `}`

- [ ] **Step 1: Write the failing tests** — names and assertions:
  - `active calories use the minute's heart rate when raised` — one minute steps 0, mean 140, 6 samples, profile age 46 → one record, energy equals `AllDayEnergy.activeKcalPerMinute(0, 140.0, 6, …)`, id `saltedRecordId("wearos_walk_kcal_<millis>", salt)`, version = passed `version`.
  - `heart rate minute replaces the cadence record of the same minute` — same minute built first with HR null (steps 110) then with HR 140: both records share one `clientRecordId`; second `clientRecordVersion` > first.
  - `distance is steps times stride` — 100 steps, 180 cm, null sex → `DistanceRecord.distance.inMeters == StrideDistance.metres(100, 180.0, null)`, id prefix `wearos_walk_distance_`, version 100.
  - `hour total is basal plus active` — `HourEnergy(h, h+1h, 70.0, 30.0)` → energy 100 kcal, id `wearos_total_kcal_<hourMillis>`.
  - `hour records carry the offset in force` — an hour on 2026-03-29 02:00 UTC in `Europe/Berlin` gets `+02:00`, the hour before `+01:00`.
  - `minutesFromHeartRate groups by minute` — samples at :00:05, :00:35, :01:10 (bpm 100, 110, 90) → `{:00 → (105.0, 2), :01 → (90.0, 1)}`.
  - `own ids are recognised, salted or not` — `"wearos_walk_kcal_1@s"` and `"wearos_workout_energy_9"` true; `"wearos_steps_1"` and null false.

- [ ] **Step 2: Run to verify they fail** — `./gradlew :devices:wearos:testDebugUnitTest --tests '*WearOsDayEnergyImportTest'` → FAIL (unresolved `WearOsDayEnergyImport`).

- [ ] **Step 3: Implement** `WearOsDayEnergyImport.kt` (offset per instant via `zone.rules.getOffset`, as `WearOsStepsImport` does) and remove `WearOsStepsImport.activeCaloriesRecords` with its tests moved.

- [ ] **Step 4: Run** `./gradlew :devices:wearos:testDebugUnitTest` → PASS except the sync-service callers fixed in Task 3 (compile errors there are expected; if the module does not compile, do Task 3 Step 3 in the same commit).

- [ ] **Step 5: Commit** — `feat(wearos): record builders for all-day energy, distance and hourly totals`

---

### Task 3: Sync wiring

**Files:**
- Modify: `devices/wearos/src/main/kotlin/tech/mmarca/openwatch/devices/wearos/WearOsWatchSyncService.kt` (`pullHeartRate`, `pullSteps`, sync sequence ~line 209, companion permissions)
- Modify: `devices/wearos/src/main/kotlin/tech/mmarca/openwatch/devices/wearos/WearOsSyncProgress.kt` (`noBodyHeight`)
- Modify: `core/devices-api/src/main/kotlin/tech/mmarca/openwatch/devices/core/sync/DeviceSyncPort.kt` (`HealthSeries.DISTANCE`, `SkipReason.NO_BODY_HEIGHT`) and wherever `NO_BODY_WEIGHT` is rendered as a card (find with `grep -rn NO_BODY_WEIGHT --include=*.kt`), adding the height counterpart text: "Add your height in OpenWatch's profile to get distance."
- Test: `devices/wearos/src/test/kotlin/tech/mmarca/openwatch/devices/wearos/WearOsWatchSyncServiceTest.kt`

**Interfaces:**
- Consumes: Task 2 (`WearOsEnergyMinute`, `WearOsDayEnergyImport.*`), `HealthConnectReads.forEachSyncRecordPage`, `readExerciseSessionRanges`, existing `bodyWeightKg`, `bodyProfile` lambdas, `insertEstimates`.
- Produces: unchanged public behaviour of `DeviceSyncPort`; new skip reason `SkipReason.NO_BODY_HEIGHT` on `HealthSeries.DISTANCE`.

- [ ] **Step 1: Write the failing tests** (in the existing style of the `walking energy …` tests at lines ~440–600, which must keep passing unchanged):
  - `a heart-rate-only minute writes active calories` — HR page with 6 samples at 140 bpm in a minute that has no step row, weight 80, age known → one `ActiveCaloriesBurnedRecord` for that minute.
  - `distance comes with the steps when the height is known` — step minute 100 steps, height 180 → one `DistanceRecord`.
  - `no height writes steps and active calories but no distance or total` — height null → steps + active written, no `DistanceRecord`, no `TotalCaloriesBurnedRecord`, result has `SkipReason.NO_BODY_HEIGHT` once.
  - `an hour total covers basal and own active energy` — profile complete, two step minutes in one hour → one `TotalCaloriesBurnedRecord` whose kcal = `AllDayEnergy.basalKcal(profile, w, coveredMinutes, today)` + the written active kcal.
  - `minutes inside a workout get no all-day distance` — session spanning the minute → no `DistanceRecord` (mirrors the existing energy rule).
  - `without the distance write grant no distance is tried and the grant is named` — `WRITE_DISTANCE` withheld → result withholds it on `HealthSeries.DISTANCE`.

- [ ] **Step 2: Run to verify they fail** — `./gradlew :devices:wearos:testDebugUnitTest --tests '*WearOsWatchSyncServiceTest'` → the six new tests FAIL.

- [ ] **Step 3: Implement**
  - `pullHeartRate` also returns this sync's per-minute heart rate (`WearOsDayEnergyImport.minutesFromHeartRate` over `page.values`' time/bpm), e.g. as `Map<Instant, Pair<Double, Int>>?` (null = not paired, replacing the Boolean).
  - In `pullSteps.writeBatch`, replace `WearOsStepsImport.activeCaloriesRecords(minutes, weightKg, …)` with `WearOsDayEnergyImport.activeCaloriesRecords(...)` over `WearOsEnergyMinute`s that merge each due step minute with that minute's heart rate; keep every existing gate (grants, `readsOtherAppsDataNow`, sessions, weight notices) as is.
  - After `pullSteps`, one new private `writeDayEnergy(device, progress, steps, heartRate)` pass for what `writeBatch` cannot see: heart-rate minutes with no step row this sync (same gates, same session filter), then distance for the step minutes outside sessions (needs `WRITE_DISTANCE`; no height → `progress.noBodyHeight()`), then hourly totals for every clock hour touched by this sync's step or heart-rate minutes (needs `WRITE_TOTAL_CALORIES_BURNED`, weight, and `basalKcal != null`; own active kcal per hour read with `forEachSyncRecordPage(ActiveCaloriesBurnedRecord::class, hourStart, hourEnd)` filtered by `isOwnActiveOrWorkoutEnergyId`). All writes through `insertEstimates` (optional, never fail the sync). Version = `clock().toEpochMilli()`.
  - Add `WRITE_DISTANCE` and `WRITE_TOTAL_CALORIES_BURNED` to the permissions this device requests (where `WALKING_ENERGY_PERMISSIONS` is built and wherever the app requests the Wear OS device's grants — follow `ACTIVE_CALORIES_WRITE_PERMISSION`'s references).
  - Log one tester line like the existing steps line: `"day energy: N active minute(s) (H from heart rate), D m distance over M minute(s), T hour total(s)"`.

- [ ] **Step 4: Run** `./gradlew :devices:wearos:testDebugUnitTest` → PASS, including every pre-existing `walking energy …` test.

- [ ] **Step 5: Commit** — `feat(wearos): all-day energy from heart rate, distance and hourly totals in the sync`

---

### Task 4: Daily total calories to the watch

**Files:**
- Modify: `core/health-api/src/main/kotlin/tech/mmarca/openwatch/healthconnect/HealthConnectReads.kt` (`DayTotals.totalKcal`)
- Modify: `core/health/src/main/kotlin/tech/mmarca/openwatch/healthconnect/HealthConnectGateway.kt:141-163` (`readDayTotals` adds `TotalCaloriesBurnedRecord.ENERGY_TOTAL` when its read grant is held)
- Modify: the app's Health Connect permission declarations/requests (manifest + `HealthDataCatalog.kt`) to add `READ_TOTAL_CALORIES_BURNED`
- Modify: `wearlink/src/main/kotlin/tech/mmarca/openwatch/wearlink/WearLinkDaily.kt` (`DaySummary.totalKcal`, 11th field)
- Modify: `devices/wearos/src/main/kotlin/tech/mmarca/openwatch/devices/wearos/WearOsDaySummaries.kt` (fill `totalKcal`, clamped to `MAX_KCAL`)
- Test: `wearlink/src/test/.../WearLinkDailyTest.kt`, `devices/wearos/src/test/.../WearOsDaySummariesTest.kt`

**Interfaces:**
- Produces: `DayTotals(activeKcal, distanceMetres, totalKcal: Double? = null)`; `DaySummary(..., distanceMetres, totalKcal: Int? = null)`; `PUT_DAY` gains a trailing `<totalKcal>` field (`-` for none).

- [ ] **Step 1: Write the failing tests**
  - `WearLinkDailyTest`: `an eleven-field line round-trips the total` — `format(DaySummary(20_400, totalKcal = 2_100))` ends with `" 2100"` and `parse` returns `totalKcal == 2100`; `a ten-field line from an older phone parses with no total` — the pre-change line parses with `totalKcal == null`; `a total past MAX_KCAL refuses the line`.
  - `WearOsDaySummariesTest`: `the day's total calories reach the summary, rounded and clamped` — `DayTotals(totalKcal = 2_100.4)` → `2_100`; `25_000.0` → `MAX_KCAL`.

- [ ] **Step 2: Run to verify they fail** — `./gradlew :wearlink:test :devices:wearos:testDebugUnitTest --tests '*Daily*' --tests '*DaySummaries*'` → FAIL.

- [ ] **Step 3: Implement**: `FIELDS` stays the minimum (10) and parse reads an optional 11th; `LIMITS` gains `MAX_KCAL`; update the KDoc line format. The watch already ignores trailing fields, so an old watch is unaffected.

- [ ] **Step 4: Run** the same command plus `./gradlew :core:health:testDebugUnitTest` → PASS.

- [ ] **Step 5: Commit** — `feat: carry the day's total calories to the watch`

---

### Task 5: Calories and Distance complications

**Files:**
- Modify: `wear/src/main/kotlin/tech/mmarca/openwatch/wear/MetricComplications.kt` (two subclasses)
- Modify: `wear/src/main/AndroidManifest.xml` (two `<service>` entries copied from `StepsComplicationService`, `SUPPORTED_TYPES` = `SHORT_TEXT`, `UPDATE_PERIOD_SECONDS` = `300`)
- Modify: `wear/src/main/res/values/strings.xml` (labels/units if not already present; reuse `metric_active_calories`-style strings and existing distance unit formatting from `ui/components/MetricFormat.kt`)
- Test: `wear/src/test/kotlin/tech/mmarca/openwatch/wear/PhoneDaysTest.kt` or a new `PhoneDayComplicationsTest.kt`

**Interfaces:**
- Consumes: `wearGraph.phoneDays.days: StateFlow<Map<Long, DaySummary>>` (Task 4's `totalKcal`, existing `distanceMetres`).
- Produces: `class CaloriesComplicationService : MetricComplicationService()`, `class DistanceComplicationService : MetricComplicationService()`, and a pure helper `internal fun phoneDayReading(days: Map<Long, DaySummary>, today: LocalDate, value: (DaySummary) -> Int?): Int?` that the services call (today's value only; yesterday's never shows after midnight).

- [ ] **Step 1: Write the failing tests** — `today's total shows, yesterday's does not` (days `{today: totalKcal 2100, yesterday: 1900}` → 2100; days `{yesterday: 1900}` → null); `no value is null, not zero`.
- [ ] **Step 2: Run** `./gradlew :wear:testDebugUnitTest --tests '*PhoneDay*'` → FAIL.
- [ ] **Step 3: Implement** the helper and the two services (`read` ignores the store and uses `phoneDays`; valid until local midnight; null → the existing `TILE_NO_VALUE` dash; distance formatted with the unit system the Distance tile already uses).
- [ ] **Step 4: Run** `./gradlew :wear:testDebugUnitTest` and `./gradlew verifyWearCi` → PASS; merged manifest still has no `INTERNET`.
- [ ] **Step 5: Commit** — `feat(wear): calories and distance complications`

---

### Task 6: On-device check, docs, push

**Files:**
- Modify: `docs/notes/pixel-watch-status.md` (journal entry, M4 marked done or what remains), `docs/research/pixel-watch-sensor-map.md` (metric table rows for calories/distance), `CHANGELOG.md` if the fork records unreleased changes there.

- [ ] **Step 1:** Full gates: `./gradlew verifyCi verifyWearCi` and `python3 scripts/sync-wiki.py --repo mstrent/OpenWatch --check` → both pass.
- [ ] **Step 2:** Install phone (`adb install -r app/build/outputs/apk/debug/app-debug.apk`) and watch (`scripts/wear-install.sh --serial <w>`), set height/birth year/sex in OpenWatch's profile, wear ~2 h including a 10-min walk, sync.
- [ ] **Step 3:** Check in Health Connect (or via `adb logcat | grep 'day energy'`): OpenWatch distance, active and total calories present; distance within ~10 % of Health Services' daily distance from a 2-min probe (`HealthProbeActivity`, `Daily Distance`); the hour totals sum to roughly resting + active; a few minutes of hard non-walking effort (e.g. stationary bike, or brisk arm work at a raised heart rate) produce heart-rate active minutes (the log line's `H`). Both complications show today's values on the stock face after a sync. The spec's "with Google Health present, totals do not double" rests on Health Connect's app priority and is not exercised here (nobody in the family runs Google Health); note that in the journal.
- [ ] **Step 4:** Record results in the status doc journal and sensor map; commit and push to `origin pixel-watch-support`.
