# Skin temperature: "right now" card, and Google-Health-style tile + complication

Status: proposed 2026-11-26, awaiting Matt's approval. Fork-only (`pixel-watch-support`).

## Intent

Matt wants two things on his Pixel Watch 5:

1. **For fun:** skin temperature *right now*, and graphed over the day, as a card on the
   OpenWatch dashboard (the in-app "tile").
2. **A Wear OS tile and a watch-face complication that work like Google Health's:** last
   night's skin temperature as a *variation from the wearer's own baseline* (Google:
   "the variation in your skin temperature taken from your wrist while you sleep",
   baseline after 3 nights; nightly delta such as +0.12 °C).

## Goals

- Record skin temperature all day (not only while sleep is held) on watches whose source is
  Pixel's raw `com.google.sensor.skin_temperature`, when the existing skin setting is on.
- A new dashboard card "Skin temp now": latest reading, and a detail screen with today's line.
- A Wear OS tile and a complication showing last night's variation from baseline.

## Non-goals

- Daytime readings in Health Connect (Matt decided: **watch only**). Health Connect keeps only
  real nights, exactly as today.
- Any phone-side change. The phone app and its skin import stay as they are.
- Changing the existing night "Skin temperature" card, Samsung watches' behaviour, or SpO2.
- Clinical interpretation (fever, cycle). Numbers only, as Google shows them.

## Anti-goals (would be a failure even if it works)

- Fake "nights" appearing in Health Connect from daytime minutes.
- A noticeable battery hit from waking the processor for every daytime reading.
- A variation shown before a baseline exists (a night against itself reads as "no change").

## Constraints found in the code

- The phone imports every `WearMetrics.SKIN_TEMPERATURE` minute and splits them into
  "night runs" by 30-min gaps (`WearOsSkinTemperatureImport`). Anything stored under that
  metric reaches Health Connect as a night.
- Metrics sync per capability key; a metric whose key is never advertised is never served.
- Minute rows keep one week by default (`WearMetric.retentionMillis = WEEK_MILLIS`); a
  baseline of up to 30 nights needs more.
- `SensorSkinTemperatureSource` registers with report latency 0 (made for night use).
  All-day use must batch: on Pixel the hub delivers batches about every 25 s anyway (day of
  wear, 2026-11-26), so latency is not free — prefer the non-wake-up sensor with a long
  report latency in the daytime.
- The phone's baseline rule (`WearOsSkinTemperatureImport`): median of the other nights'
  medians, minimum 3 nights, at most 30. The watch uses the same numbers so both agree.

## Approaches considered

- **A. Separate watch-only metric for daytime minutes (chosen).** A new
  `WearMetrics.SKIN_TEMPERATURE_DAY` whose key is never in the advertised capabilities, so
  sync never serves it. Night minutes (sleep held) keep going to `SKIN_TEMPERATURE` exactly
  as now. No protocol or phone change; old phones stay correct.
- B. One metric with a "daytime" flag the phone filters. Needs a protocol change and a phone
  update; an older phone would import fake nights. Rejected.
- C. Record everything into the existing metric and let the phone cut by sleep sessions.
  Phone change, and fragile where no sleep session exists. Rejected.

## Design

**Recording.** `RecorderPlan`: skin runs when the setting is on, the grant is held, and either
sleep is held (as now) or — on a raw-sensor watch — the watch is worn. The recorder routes each
minute: sleep held → `SKIN_TEMPERATURE` (synced, unchanged); otherwise →
`SKIN_TEMPERATURE_DAY` (watch only, two-day retention). Daytime registration batches
(non-wake-up, long latency); night keeps today's registration. Samsung watches unchanged.

**Baseline storage.** `SKIN_TEMPERATURE` retention grows to `MONTH_MILLIS` + 2 days, so the
watch holds ≥30 nights of minutes (~500 rows a night; small). Sync is cursor-based, so the
phone is unaffected.

**Night variation (pure, tested).** A night's value = median of its minutes in
`WearReadings.nightWindow`. Baseline = median of the up-to-30 previous nights' values, only
once there are ≥3. Variation = last night − baseline. Fahrenheit deltas ×1.8. Signed, one
decimal ("+0.3°", "−0.1°", "0.0°").

**Dashboard card "Skin temp now"** — new `MetricTile.SKIN_TEMPERATURE_NOW`: `SAMPLED`,
`SENSOR`, day period, offered where `WatchSensors.skinSensor`. Current = newest minute from
either metric if under ~15 min old, else no reading. Detail: today's line (both metrics merged,
quarter-hour means as the night card does), low and high.

**Wear OS tile "Skin temperature"**, built like the existing metric tiles: headline last night's
variation, "vs your baseline"; a chart of the last 7 nights' variations around zero. Before a
baseline: "Wear your watch to sleep — N more nights to set your baseline" (Google's wording
idea). No night yet: "No reading last night".

**Complication "Skin temperature"**: `SHORT_TEXT` "+0.3°" with the thermometer icon; "--"
without a variation. Tap opens the app, as the others do. Refreshed when a night's data lands,
the way the other complications/tiles are refreshed.

**Settings text**: the skin row says it records all day on this watch where that applies.

## Left to the builder

Exact strings and translations policy (follow the repo's translation gate), chart styling via
`TileChart`, tile preview drawable, refresh hooks, and the precise daytime sensor latency.

## Verification

Unit tests for routing, the baseline/variation math (0, 2, 3, 31 nights; °F), and the offer.
On the watch: daytime rows appear only in the watch-only metric; a phone sync writes no new
skin record during the day; the card shows a live value; the tile and complication render
(with "N more nights" until three nights exist). Battery: a debuggable-build protocol day
compared against 2026-11-26's 2.24 %/h doze baseline.
