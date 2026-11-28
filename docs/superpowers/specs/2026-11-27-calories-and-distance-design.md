# All-day calories and distance (M4)

> **Dropped 2026-11-27, not built.** Upstream derives distance in OpenVitals with a user-entered
> stride; Matt wants no fork to maintain. See the Decisions log in `docs/notes/pixel-watch-status.md`.

Status: proposed 2026-11-27, revised the same day to approach B (OpenWatch's own estimate
everywhere); awaiting Matt's approval. Built on `pixel-watch-support`; upstream-compatible in
spirit (raw sensors + own math, no vendor library).

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
- **Health Services** on Pixel Watch 5 delivers passive `Calories`/`Distance` to an ordinary app,
  Workspace-only too, but its calories (and presumably its stride) follow the watch's own
  Settings > Health Profile, which OpenWatch cannot read. Using it would make wearers keep two
  profiles; Matt rejected that ("I don't like that double entry"). **Not used.** Useful only as a
  reference to compare our estimates against during development (the debug probe reads it).
- Upstream already follows this approach: cadence-based all-day active calories, heart-rate
  (Keytel) workout calories (`wearlink/.../WorkoutMetrics.kt` on upstream/main).
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
- Any new setting or toggle; any second profile the wearer must keep.
- Google's Health Services or any other vendor library in the release build.
- Changing how workouts compute their own calories/distance.

## Anti-goals (would make this a failure even if it works)

- **Doubled totals** in Health Connect: two of OpenWatch's own sources for the same minute, or
  active calories counted inside a total and again on top of it.
- **Double entry**: the wearer having to enter their body data in more than OpenWatch's profile.
- **Silent wrong numbers** from a profile value the wearer never set (no population defaults
  standing in for a missing weight or height). Wrong-but-plausible is worse than absent.

## Approaches considered

- **A. Health Services only.** Google's numbers; Pixel/Wear OS only; new Google dependency;
  unknown profile source without Fitbit; overlaps OpenWatch's active calories.
- **B. OpenWatch's own estimate only, on the phone.** Distance = steps × stride from height;
  total = active + Mifflin-St Jeor resting burn. Every watch, no new dependency; an estimate
  (distance ±10 % uncalibrated).
- **C. Both.** Health Services where the watch offers it, B everywhere else. Chosen first, then
  dropped: Health Services' numbers depend on the watch's Health Profile, a second profile.
- **Chosen: B** (Matt, 2026-11-27): one profile, no new dependency, every watch, upstream's own
  approach extended. Cost: no Google stride calibration.

## Design (decided)

### Sources, per metric, per minute (all computed by OpenWatch on the phone)

| Metric | From |
|---|---|
| Active calories | heart rate (Keytel) when raised by effort, else step cadence (`WalkingEnergy`, as today) |
| Total calories | active calories + resting burn (Mifflin-St Jeor, `BasalMetabolicRate`) per minute |
| Distance | steps × stride length from height (and sex), stride lengthening with cadence |

- Inputs already on the phone after a sync: step minutes, heart-rate samples, the body profile
  (weight from Health Connect, height/age/sex from `BodyProfile`).
- Exactly **one OpenWatch record per metric per minute** in Health Connect, keyed by a stable
  client record id so a re-sync replaces rather than duplicates. A minute is re-computed when its
  inputs grow (late steps or heart rate), like the step records today.
- **Coexistence with Google Health** is left to Health Connect's app priority (it aggregates one
  app per data type), so totals do not double. No OpenWatch logic for it, no setting.
- Minutes inside a recorded workout: keep the workout's own energy/distance and do not also write
  all-day records for those minutes (the builder confirms how `WearOsStepsImport` already avoids
  this for active calories — it reads "the exercise sessions present").

### Watch

- **No new watch recording.** Everything is computed from data the watch already sends.
- Complications: **Calories** and **Distance**, today's running figure, same style and update
  behaviour as the Steps complication, fed by the phone's daily figures (`PhoneDays`). The figure
  is as fresh as the last sync (30-min auto-sync), as the tiles are today.
- The existing Distance and Active-calories tiles keep their source (phone daily figures), which
  now holds OpenWatch's own numbers for wearers without Google Health.

### Phone

- Pure calculations in `health-calculations` (tested): per-minute active energy (heart rate or
  cadence), resting energy, stride/distance.
- `devices/wearos`: write `DistanceRecord` and the total-calories records per minute alongside
  `WearOsStepsImport`; switch its `ActiveCaloriesBurnedRecord` minutes to the new rule.
- `PhoneDays`/`readDayTotals`/`WearLinkDaily` additionally carry total calories if the
  complication shows it.

### Missing profile

- No weight → no calories of any kind (as today), and the existing "no body weight" sync notice.
- No height → no distance (no population default); a sync notice like the weight one.
- Age/sex as `WorkoutEnergy` already does: mean of both sexes without sex; no Keytel without age
  (cadence only).

## Left to the builder

- The heart-rate threshold for "raised by effort" (suggest: above the wearer's resting heart rate
  plus a margin, or a fraction of heart-rate reserve; Keytel is not valid near rest), and the
  rule for minutes with sparse heart rate.
- Stride-length formula (suggest height × 0.413 women / 0.415 men, mean without sex) and
  whether walking vs running cadence changes it.
- Whether "Calories" on the complication shows total or active (suggest **total**, as Google does,
  with active on the existing tile).
- Exact wire format, capability names, record id prefixes; following existing patterns.
- Where the resting heart rate for the threshold comes from (the phone already computes one).

## Open — verify early in the build

1. **Health Connect's total-calories aggregate**: confirm whether it already derives a total from
   active + basal records when no total records exist. If so, write `BasalMetabolicRateRecord`
   (or per-minute resting energy as the API allows) instead of `TotalCaloriesBurnedRecord`, so the
   active share is not counted twice.

Answered during design (kept for the record): Health Services' calories follow the watch's
Settings > Health Profile (weight +20 kg moved the resting burn 1.084 → 1.244 kcal/min and back;
see the sensor map). That is why it is not used.

## Done when

- On Matt's Pixel Watch 5: a day's distance and total calories appear in Health Connect under
  OpenWatch; compared with Health Services' daily figures (debug probe) for the same day, distance
  within ~10 % and resting/total calories plausible for his profile.
- Active calories rise on a bike ride or strength session where steps stay low.
- Complications show today's values on the stock face.
- With Google Health present: Health Connect's daily totals do not double.
- `./gradlew verifyCi verifyWearCi` and the wiki check pass.
