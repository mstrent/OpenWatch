# Skin temperature now + variation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** All-day skin temperature on raw-sensor watches, a "Skin temp now" dashboard card, and a Google-Health-style variation tile and complication.

**Architecture:** Daytime minutes go to a new watch-only metric that is not in `WearMetrics.SERIES`/`ALL`, so the link never announces or serves it; night minutes stay in `SKIN_TEMPERATURE` (synced, unchanged). A pure `SkinVariation` computes nightly medians, a ≥3-night baseline and last night's delta; the tile and complication read it from the store like the existing metric tiles.

**Tech Stack:** Kotlin, Wear Compose Material 3, ProtoLayout tiles, androidx complications, JUnit (recording-core and wear unit tests).

**Spec:** `docs/superpowers/specs/2026-11-26-skin-temperature-now-and-variation-design.md`

## Global Constraints

- Daytime readings never reach the phone or Health Connect.
- No phone-side change; the wire protocol is unchanged.
- Baseline rule as the phone's: median of previous nights' medians, min 3, max 30 nights.
- Samsung watches behave exactly as before (night only).
- Merged watch manifest gains no network permission.
- Gate before push: `ANDROID_HOME=$HOME/Android/Sdk ./gradlew verifyWearCi` and `python3 scripts/sync-wiki.py --repo mstrent/OpenWatch --check`.

## Review Focus

- A night spanning midnight is one night (`WearReadings.nightWindow`, noon to noon) — baseline test uses it.
- Daytime minutes written during a sleep hold transition land in exactly one metric (routing test).
- Stale "now": newest reading older than 15 min shows no value, not an old one (readings test).
- Fahrenheit deltas are ×1.8 with no +32 (format test).
- Off the wrist the daytime recorder is off (plan test: `worn=false` → skin off unless sleep held).

---

### Task 1: Record all day into a watch-only metric

**Files:** Modify `wear/recording-core/.../WearMetric.kt`, `RecorderPlan.kt`, `wear/src/main/.../SkinTemperatureRecorder.kt`, `SensorSkinTemperatureSource.kt`, `WearGraph.kt`, `WearAppService.kt`. Test: `RecorderPlanTest.kt`, `WearMetricsTest.kt`, `SkinTemperatureRecorderTest.kt`.

**Produces:** `WearMetrics.SKIN_TEMPERATURE_DAY` (key `"skd"`, retention 2 days, same codec as `SKIN_TEMPERATURE`, not in `SERIES`/`ALL`); `SKIN_TEMPERATURE.retentionMillis = MONTH_MILLIS + 2 days`; `RecorderPlan.Inputs.skinAllDay: Boolean = false`; `SkinTemperatureRecorder(store, source, debug, isSleepHolding: () -> Boolean)`; pure `SkinTemperatureRecorder.metricFor(sleepHolding: Boolean)`.

- [ ] Tests: plan — `skinAllDay && worn && setting && grant` → skin on without a hold; `skinAllDay && !worn` → off; `!skinAllDay` → unchanged (hold needed). Metrics — `"skd"` not in `ALL` keys; skin retention ≥ 31 days. Recorder — `metricFor(true) == SKIN_TEMPERATURE`, `metricFor(false) == SKIN_TEMPERATURE_DAY`.
- [ ] Run, see them fail; implement; source prefers the non-wake-up sensor (`!isWakeUpSensor`, falling back to any); wire `skinAllDay = skinSource is SensorSkinTemperatureSource && available` through `WearGraph` → `planInputs()`; run green; commit.

### Task 2: SkinVariation (pure)

**Files:** Create `wear/src/main/.../SkinVariation.kt`; test `wear/src/test/.../SkinVariationTest.kt`.

**Produces:** `object SkinVariation { data class Night(date: LocalDate, medianCelsius: Double); sealed interface Status { NoNight; Building(nightsNeeded: Int); Delta(celsius: Double, nights: List<Pair<LocalDate, Double?>>) }; fun nights(rows: List<SkinMinute>, zone: ZoneId): List<Night>; fun status(nights: List<Night>, today: LocalDate): Status; fun formatDelta(celsius: Double, unitSystem: UnitSystem): String }`. A night = minutes in `nightWindow(date)`; needs ≥ 60 minutes to count (as the phone's minimum run, check `WearOsSkinTemperatureImport` and match). `Delta.nights` = last 7 dates' deltas vs the baseline as of each night (null where no night).

- [ ] Tests: 0 nights → NoNight; last night + 2 earlier → Building(1); last night + 3 → Delta = last − median(prev); 31 earlier nights → only 30 newest used; midnight-spanning night is one night; `formatDelta(0.25, METRIC) == "+0.3°"`, `(−0.1)` → `"−0.1°"`, `(0.1, IMPERIAL) == "+0.2°"`.
- [ ] Fail, implement, pass, commit.

### Task 3: "Skin temp now" dashboard card

**Files:** `MetricTile.kt` (new `SKIN_TEMPERATURE_NOW`, SAMPLED, SENSOR, day), `MetricOffer.kt` (offer when `WatchSensors.skinSensor`), `WatchSensors.kt` (+`skinSensor: Boolean = false`), `WearGraph.kt` sensors, `WearReadingsSource.kt` (read today's skin rows from both metrics), `WearReadings.kt` (`DayReadings.skinLine`, `latestSkin`; card: current = newest if ≤ 15 min old, line = quarter-hour means), `MetricFormat.kt`, strings. Tests in existing `MetricOfferTest`/`WearReadingsTest` (wear/src/test).

- [ ] Tests: offered only with `skinSensor`; current null when newest is 16 min old; line merges night+day rows.
- [ ] Fail, implement, pass, commit.

### Task 4: Tile and complication

**Files:** Create `SkinTemperatureTileService` (in `MetricTiles.kt`), `SkinTemperatureComplicationService` (in `MetricComplications.kt`), `TileChart.deltas(values: List<Double?>): Png?` (bars around a zero line, 7 slots, no hour labels), drawable `ic_metric_skin_temperature.xml`, `tile_preview_skin_temperature` (copy of an existing preview pattern), manifest entries (SHORT_TEXT, 300 s / tile), strings. Shared reader `readSkinVariation(store, nowMillis): SkinVariation.Status` reading `SKIN_TEMPERATURE` since now − 32 days.

- [ ] Value/caption mapping (unit-tested through a pure `SkinVariation` → text function where possible): Delta → "+0.3°" / "vs your baseline"; Building(n) → "—" / "n more nights to set your baseline"; NoNight → "—" / "No reading last night".
- [ ] Build, install on watch, add tile and complication, check render; commit.

### Task 5: Settings copy, docs, on-watch verification

- [ ] Skin settings row text mentions all day where `skinAllDay`.
- [ ] On watch: enable skin; after minutes, `skd` rows exist (debuggable build), a phone sync logs no new skin record; card shows a value.
- [ ] Gate, update `docs/notes/pixel-watch-status.md`, push.
