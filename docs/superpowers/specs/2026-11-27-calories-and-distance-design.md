# All-day calories and distance (M4)

Status: proposed 2026-11-27, awaiting Matt's approval. Fork-only (`pixel-watch-support`).

## Intent

Family members on Workspace-only Pixel Watches have no Google Health, so nothing writes their
all-day distance or total calories to Health Connect, and their watch shows no calories or
distance at a glance. Matt wants both: "nice metrics", in Health Connect (Zepp, Strava, Health
Connect's own screens) **and** on the watch (tiles and watch-face complications), next to the
steps and heart rate that already work.

The people who will use this do **not** run Google Health. It should still coexist with Google
Health without any setting (Matt: "ideally it'd coexist without needing toggles").

## What exists today (verified in code, 2026-11-27)

- **Active calories, all day:** the phone writes `ActiveCaloriesBurnedRecord` per minute from the
  watch's step cadence (`WalkingEnergy`, in `WearOsStepsImport`), only when a body weight is known.
- **Workout calories and distance:** written per workout (`WearOsWorkoutImport`,
  `WearOsWorkoutEnergy`), heart-rate based (Keytel) with Mifflin-St Jeor resting share
  (`health-calculations/.../WorkoutEnergy.kt`, `BasalMetabolicRate.kt`).
- **Floors, all day:** barometer, `WearOsFloorsImport`. Out of scope.
- **Watch tiles** `MetricTile.DISTANCE` and `ACTIVE_CALORIES` exist and show the phone's daily
  figures (`PhoneDays`, pushed after each sync from Health Connect totals *from any app*,
  `HealthConnectGateway.readDayTotals`). For a Workspace-only family member the distance tile is
  empty because nothing writes all-day distance.
- **Complications** exist for heart rate, steps, floors, skin temperature; none for calories or
  distance.
- **Health Services** on Pixel Watch 5, Workspace-only, delivers passive `Calories`/`Distance`
  deltas and `Daily Calories`/`Daily Distance` totals to an ordinary app (probe
  `reports/probe-20261127-172506.jsonl`). Its calories are **total** (a resting minute was
  ~1.19 kcal, ~1,700 kcal/day): resting burn included.
- Phone manifest already declares `WRITE_DISTANCE`, `WRITE_TOTAL_CALORIES_BURNED`,
  `WRITE_ACTIVE_CALORIES_BURNED`, `WRITE_BASAL_METABOLIC_RATE`. The body profile (weight from
  Health Connect, height/age/sex from the app's `BodyProfile`) is already read for workouts.

## Goals

1. **All-day distance** in Health Connect, per minute, on every watch with a step counter.
2. **All-day total calories** in Health Connect, per minute, on every watch.
3. **Better all-day active calories:** per minute, from heart rate when the heart rate is clearly
   raised by effort, otherwise from step cadence as today. Catches cycling, strength, yard work.
4. **Watch:** a Calories complication and a Distance complication; the existing Distance and
   Calories tiles then fill for Workspace-only wearers too.
5. **Coexists with Google Health without a setting.**

## Non-goals

- Floors (already done), SpO2/HRV/respiratory (no route), workouts through `ExerciseClient`.
- Any new setting or toggle.
- Changing how workouts compute their own calories/distance.
- Upstream acceptability (the Health Services dependency is fork-only; see `AGENTS.md` fork section).

## Anti-goals (would make this a failure even if it works)

- **Doubled totals** in Health Connect: two of OpenWatch's own sources for the same minute, or
  active calories counted inside a total and again on top of it.
- **A network permission on the watch** (the no-`INTERNET` invariant; Health Services client
  1.1.0 adds none — re-check the merged manifest).
- **Silent wrong calories** from a profile the wearer never set. Wrong-but-plausible is worse than
  absent. (Health Services uses the watch's Health Profile — see Open 1 — so this becomes a setup
  step, not a code path.)

## Approaches considered

- **A. Health Services only.** Google's numbers; Pixel/Wear OS only; new Google dependency;
  unknown profile source without Fitbit; overlaps OpenWatch's active calories.
- **B. OpenWatch's own estimate only, on the phone.** Distance = steps × stride from height;
  total = active + Mifflin-St Jeor resting burn. Every watch, no new dependency; an estimate
  (distance ±10 % uncalibrated).
- **C. Both — chosen by Matt.** Health Services where the watch offers it, B everywhere else.
  Most code, two sources to reconcile, best numbers on Pixel and still works on every watch.

## Design (decided)

### Sources, per metric, per minute

| Metric | Watch offers Health Services passive Calories/Distance | Otherwise |
|---|---|---|
| Distance | Health Services `Distance` deltas | steps × stride length (from height; sex-specific factor) |
| Total calories | Health Services `Calories` deltas (total) | active (below) + resting burn (Mifflin-St Jeor per minute) |
| Active calories | OpenWatch: heart rate (Keytel) when raised, else step cadence (`WalkingEnergy`) | same |

- **Automatic, no setting**: Health Services is used when its capability check lists the type
  for this app; otherwise the estimate.
- Exactly **one OpenWatch record per metric per minute** in Health Connect, keyed by a stable
  client record id so a re-sync replaces rather than duplicates. If both sources exist for a
  minute (e.g. Health Services appeared later), the Health Services value wins.
- **Coexistence with Google Health** is left to Health Connect's app priority (it aggregates one
  app per data type), so totals do not double. No OpenWatch logic for it.

### Watch

- New recorder over Health Services passive monitoring for `Calories` and `Distance`, opt-in only
  in the sense of capability-probed; lives in a new `wear/.../healthservices/` package shaped like
  `wear/.../samsung/`. Runs in `WearAppService` like the other recorders.
- New `WearMetrics` series (per-minute rows, `REPLACE` on the same minute, a week's retention)
  with their lines in `:wearlink` (`WearLinkProtocol`), plus capability tokens so the phone knows
  which source a watch has.
- `health-services-client` moves from `debugImplementation` to `implementation` in
  `wear/build.gradle.kts`; lockfiles/verification metadata updated.
- Complications: **Calories** and **Distance**, today's running figure, same style and update
  behaviour as the Steps complication. Data: the phone's daily figures (`PhoneDays`), plus the
  watch's own Health Services minutes for today when present so the wrist is not a sync behind.
- The existing Distance and Active-calories tiles keep their source (phone daily figures).

### Phone

- Import the new watch series and write `DistanceRecord` and `TotalCaloriesBurnedRecord` per
  minute (`devices/wearos`, alongside `WearOsStepsImport`).
- For minutes without Health Services: compute distance and total calories from the step
  minutes, the heart rate samples and the body profile (`health-calculations`, pure and tested).
- Change the all-day `ActiveCaloriesBurnedRecord` minutes to the heart-rate-or-cadence rule.
- `PhoneDays`/`readDayTotals` additionally carries total calories if a tile/complication shows it.

### Missing profile

- No weight → no calories of any kind (as today), and the existing "no body weight" sync notice.
- No height → stride from a population default is **not** used; distance from the estimate is
  skipped (Health Services distance still written). Same rule for age/sex as `WorkoutEnergy`
  already applies (mean of both sexes without sex; no Keytel without age).

## Left to the builder

- The heart-rate threshold for "raised by effort" (suggest: above the wearer's resting heart rate
  plus a margin, or a fraction of heart-rate reserve; Keytel is not valid near rest), and the
  rule for minutes with sparse heart rate.
- Stride-length formula (suggest height × 0.413 women / 0.415 men, mean without sex) and
  whether walking vs running cadence changes it.
- Whether "Calories" on the complication shows total or active (suggest **total**, as Google does,
  with active on the existing tile).
- Exact wire format, capability names, record id prefixes; following existing patterns.
- How the watch learns the resting heart rate it needs for the threshold (phone already has it
  in `PhoneDays`).

## Open — verify early in the build

1. ~~Where Health Services gets the wearer's profile~~ — **answered 2026-11-27**: from the watch's
   own **Settings > Health Profile** (stored in Health Services' `ProfileContentProvider`; written
   by Settings, which runs as system, and by Fitbit, the only other holder of
   `WRITE_PROFILE_INFO`). Verified on Matt's watch: raising the weight ~20 kg raised the resting
   burn from 1.084 to 1.244 kcal/min; restoring 150 lb restored 1.084 (probes
   `probe-20261127-190838`, `-191345`). So Health Services' calories are right **if the wearer
   fills in Health Profile on the watch**. OpenWatch cannot read that profile
   (`READ_PROFILE_INFO` is signature|privileged), so the two profiles (watch Health Profile for
   Health Services, OpenWatch's own body profile for the estimate) can disagree. Decision for the
   build: keep Health Services as the calories source where offered; the setup docs / onboarding
   tell family members to fill in Health Profile on the watch.
2. **Health Connect's total-calories aggregate**: confirm whether it already derives a total from
   active + basal records when no total records exist; if so, write `BasalMetabolicRateRecord`
   instead of per-minute totals on the estimate path to avoid double counting.

## Done when

- On Matt's Pixel Watch 5 with Google Health signed out or absent: a day's distance and total
  calories appear in Health Connect under OpenWatch, plausible against Health Services' daily
  figures; complications show today's values on the stock face.
- On a watch without Health Services (emulator or Samsung): the estimate path writes them.
- With Google Health present: Health Connect's daily totals do not double.
- `./gradlew verifyCi verifyWearCi` and the wiki check pass; merged watch manifest has no
  network permission.
