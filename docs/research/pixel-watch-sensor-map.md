# Research: which Pixel Watch sensors an ordinary app can read

**Status: steps 1–2 verified on a Pixel Watch 5 on 2026-11-26 (sensor list; Health Services via dumpsys and an on-watch probe), with the vendor app signed in on a consumer account; steps 3–4 (all-day batching and contention) still open.**
This is the Pixel Watch companion to [wear-sensor-map.md](wear-sensor-map.md), which answers the
same question for a Galaxy Watch7 from a `dumpsys sensorservice` capture. Nothing below is marked
verified until a capture exists; everything else is marked **reported** and dated. The motivating
setup: a Pixel Watch whose family cannot rely on the vendor's health stack (Google Health sign-in
for the whole watch was long the only supported path), leaving the watch app as the only reader of
the watch.

## The device the survey targets

**Pixel Watch 5** (GSMArena, 2026-08): announced and shipped August 2026, ships **Wear OS 7**,
which is based on Android 17 (API 37); 41 mm and 45 mm; Snapdragon W5 Gen 2 with a Cortex-M55
coprocessor. Its listed health hardware is multi-path optical heart rate, ECG electrodes, SpO2,
continuous skin conductance, skin temperature and a barometer/altimeter, plus dual-frequency
GNSS (GPS, Galileo, GLONASS, BeiDou, QZSS).

A spec sheet says what the part can measure, not what an ordinary app may read. Nothing in this
section is a claim about access: that is exactly what step 1 measures. The watch's sensor hub is
a different part from the Galaxy Watch7's, so treat the Galaxy map's FIFO sizes and gating as
hypotheses to check, not as expectations to reuse.

Wear OS 7 also changes the surfaces a watch app offers: it introduces **Wear Widgets** while
leaving Tiles supported for now (Android Developers, 2026-05). OpenWatch's five watch surfaces are
still tiles, which is why the tile question below is worth asking on the device before any
migration is discussed.

## Two ways to read a Pixel Watch

There are two doors, and the second one is the interesting one.

**The raw sensor door** is what OpenWatch uses everywhere today: `SensorManager`, the
`android.sensor.*` types, batched and recorded on the watch. It is the same code as on Galaxy and
TicWatch, and it is the only door this repository currently opens.

**Health Services on Wear OS** is Google's own on-watch health API (`MeasureClient`,
`SessionClient`, `CaloriesClient`, `GoalsClient`, `ExerciseEventsClient`, all discovered through
`CapabilitiesClient`). It is a different thing from the vendor's *app*: it ships inside Google Play
Services on the watch, and Google's own description of it is an "intermediary for sensors and
algorithms that provides apps with high-quality data" (Android Developers). What matters here is
that a third-party app is its intended audience, and that it hands over the *vendor's processed*
numbers rather than raw light: heart rate, the step pipeline's counts, calories, distance, active
steps, and, where a device supports them, spot SpO2, skin temperature, respiratory rate, HRV,
sleep and stress. On a Pixel Watch that is the difference between the watch's raw PPG (closed) and
the same heart rate the stock face displays (documented API).

Two properties decide whether it belongs in OpenWatch:

- The client is an ordinary Jetpack library, `androidx.health.services:health-services-client`,
  Apache-2.0, with its source in AndroidX. It is not a vendor `.aar`. It *talks to* Google Play
  Services at run time, so the implementation is proprietary even though the client is not, and a
  watch without Play Services gets nothing.
- `CapabilitiesClient` answers "what does *this* watch support" at run time. That is a better fit
  for a capability-gated recorder than a spec sheet is: the survey does not have to guess, it can
  ask.

Whether either door is open on a Pixel Watch whose vendor health app is signed out is the single
most important open question below, and it is answerable in ten minutes on a real watch.

## How to fill this in

All four steps need only wireless debugging on the watch, as in
[wear-os-sideload.md](../how-to/wear-os-sideload.md).

1. The sensor list and its gates, the way the Galaxy map was built:

   ```bash
   adb shell dumpsys sensorservice | sed -n '/Sensor List/,/^$/p'
   ```

   Record each sensor's name, type string and `perm:` entry. The questions that matter: whether
   `android.sensor.heart_rate`, `android.sensor.step_counter` and `step_detector`,
   `android.hardware.sensor.barometer` and a low-latency off-body sensor are present and gated the
   AOSP way, and whether Google gates any of them behind its own signature permission.

2. What Health Services offers this watch, which is the question `CapabilitiesClient` exists to
   answer. A five-line debug screen (`getSupportedDataTypes`, then `getCapabilityList` per type)
   prints the whole inventory: heart rate, steps and active steps, calories, distance, spot SpO2,
   skin temperature, respiratory rate, HRV, sleep, stress. Run it twice, once with the vendor's
   health app signed in and once out; if the two lists differ, that answer decides the whole
   approach.

3. The batching and delivery survey, with the debug watch app installed
   (`scripts/wear-install.sh`):

   ```bash
   python3 -I scripts/wear-debug-report.py --serial <watch>
   ```

   The `device` line lists every sensor with its FIFO sizes and wake-up flag; the `registered`
   lines show whether the `hub-40s` profile's batches survive in the FIFOs. The Galaxy watch hub
   was tuned to that profile; the Pixel hub is a different part and is expected to need its own
   number (or a fallback profile) if the batches drop.

4. The heart rate contention test, the one Pixel-specific risk. The stock health stack runs the
   optical sensor continuously. Third-party watch faces on Pixel Watch reported since 2022 that a
   live reader froze the heart rate until it stopped reading, and some shipped a poll-every-30-
   minutes workaround instead (Google support thread, 2022-12). Whether OpenWatch's batched, gated
   recording survives beside the stock stack, and what the stock stack does to it in return, is a
   day of logging with the debug report, not a guess. Reading heart rate through Health Services
   instead of `SensorManager` may sidestep the contention entirely, since the vendor's own pipeline
   owns the light; that is the second reason the survey is worth the day.

## Verified: the sensor list of a Pixel Watch 5 (2026-11-26)

Captured by this project with `adb shell dumpsys sensorservice` on a **Pixel Watch 5 (LTE,
`wisteria_lte`)**, build `CP3A.260905.002`, Android 17 / API 37, Health Services
`0.99.6.997808761` (versionCode 1496923), vendor health app signed in. Each sensor is listed as a
wake-up and a non-wake-up pair with the same gate; the table lists the type once.

Open to an ordinary app (AOSP gate or none):

| Type | Vendor | Gate (`perm:`) | FIFO | Note |
|---|---|---|---|---|
| `android.sensor.heart_rate` (21) | Google | `health.READ_HEART_RATE` | 3000 | processed bpm, on-change |
| `com.google.sensor.heart_rate_extended` (131086) | Google | `health.READ_HEART_RATE` | 3000 | vendor type behind the **public** HR grant; payload unknown (worth probing for beat intervals/confidence) |
| `android.sensor.step_counter` (19), `step_detector` (18) | Google | `ACTIVITY_RECOGNITION` | 3000 | AOSP way |
| `com.google.sensor.step_cadence` (131087) | Google | `ACTIVITY_RECOGNITION` | 3000 | vendor type behind a public grant |
| `com.google.sensor.skin_temperature` (65555) | TI | `health.READ_SKIN_TEMPERATURE` | 3000 | **open**: a raw skin temperature route behind the public Android 16+ grant |
| `android.sensor.pressure` (6) | Goermicro | none | 3000 | barometer, ascent works |
| `android.sensor.low_latency_offbody_detect` (34) | Google | none | 3000 | present |
| accelerometer (TDK), gyroscope (TDK), magnetometer (MEMSIC), light (AMS), gravity, linear acceleration, rotation vectors, orientation, wrist tilt, stationary/motion detect, uncalibrated variants | — | none | 3000 (rotation vectors 100, light 50) | AOSP types |

Gated by Google's own `com.google.pixelwatch.permission.READ_PRIVATE_SENSORS` (signature, not
reachable): raw PPG (`com.google.wear.sensor.ppg`, TI, FIFO 100), PPG controller, ECG and ECG lead
detector (TI), galvanic skin response (TI, FIFO 100), IMU/pressure/magnetometer temperatures, the
50/100 Hz accelerometer and gyroscope streams, gaze, raise-to-talk, one-handed gestures, glanceable
state, misaligned-charger detection.

Absent: **`android.sensor.heart_beat` (31)** — no beat-interval sensor on the raw path. No SpO2
sensor type is listed at all, open or gated.

First debug-build run (`wear-debug-report.py`, about a minute): every recorder registered under
`hub-40s` with its batch inside the FIFO — heart rate 40/3000 (wake-up), steps 200/3000, ascent
40/3000, sleep accelerometer 200/3000, off-body unbatched. Delivery over a worn day (whether the
hub honours those batches) is not measured yet.

## Verified: what Health Services lists on a Pixel Watch 5 (2026-11-26)

Read with `adb shell dumpsys activity service com.google.android.wearable.healthservices` on the
same watch (Fitbit signed in). This is the service's own table of supported types per exercise
type ("MetricsMappings", 97 of them), not yet an app's `CapabilitiesClient` answer.

- **Passive (all-day) types:** `HEART_RATE_BPM`, `PASSIVE_STEP_COUNT`, `PASSIVE_DISTANCE`,
  `PASSIVE_ALL_CALORIES`, `PASSIVE_FLOORS`, `PASSIVE_ELEVATION_GAIN`, plus `SLEEP_DETECTION` and
  `ABNORMAL_HR_DETECTION`.
- **Exercise types (96 kinds):** heart rate and its stats, calories, active duration, distance,
  steps, speed/pace, cadence, elevation, floors, location/GNSS, auto-pause/stop, swim laps, and for
  running the running dynamics (stride length, ground contact time, vertical oscillation/ratio).
- **No public type for SpO2, HRV, respiratory rate, skin temperature, stress or VO2 max.** The
  earlier claim in this doc that Health Services offers them "where a device supports it" does not
  hold for this watch.
- **Numeric types** (`65537`–`65549` passive, `131073`–`131078` per exercise) appear with no public
  name; the Fitbit app subscribes to the passive ones as `health_services.device_private.<n>`. These
  are presumably where Google's own SpO2/HRV/skin-temperature pipelines live. Whether a
  third-party app can subscribe to a `device_private` type, or is refused, is untested.

So Health Services fills the gaps OpenWatch has no raw route for (calories, distance, running
dynamics, the vendor's step and floors pipeline, sleep detection), but not the vitals.

## Verified: what an ordinary app actually receives (probe, 2026-11-26)

`HealthProbeActivity` (debug builds only, `wear/src/debug/`) run on the same watch, worn, for
120 s, with the debug watch app's own recorders running alongside. Fitbit / Google Health was
signed in, **on the consumer Gmail account that is also on this watch**. Repeat the run on a
Workspace-only watch before relying on the Health Services rows.

- **Health Services capabilities for this app** equal the dumpsys table above: passive
  `HeartRate`, `Steps`, `Distance`, `Calories`, `Floors`, `Elevation Gain` and their daily totals;
  user activity states passive/exercise/asleep; measure: `HeartRate` only; exercise: the same ~90
  exercise types.
- **Health Services delivers**: live heart rate through `MeasureClient` every ~2 s with
  `ACCURACY_HIGH` (`UNAVAILABLE_DEVICE_OFF_BODY` when not worn); passive heart rate at the same
  rate; passive calories per minute, daily steps, distance, calories (including resting energy),
  floors.
- **The vendor's private passive types are closed.** Subscribing to
  `health_services.device_private.<n>` fails with `SecurityException: Missing permissions:
  [com.google.android.wearable.healthservices.permission.OEM_OPAQUE_DATA_ACCESS]`, and that
  permission is `signature|privileged`. Not reachable by any app Google did not sign.
- **Raw heart rate and Health Services heart rate run side by side without freezing** over two
  minutes (same values, both ~0.5 Hz). The all-day contention test (question 4) still stands.
- **`heart_rate_extended`** is not beat intervals: `values = [bpm, 0, status, 0…]`, where the status
  follows the sensor's accuracy (3/2/1, and -1 off the wrist). No HRV route here.
- **`skin_temperature`** delivers real wrist temperature in °C in `values[0]` (resolution 1/128 °C)
  about every 30 s: 24.7 °C off the wrist, rising from 29.8 to 30.6 °C after putting it on.
- **`step_cadence`** reports `-1` at rest; its walking values are not yet seen.

## Verified: a day of wear (2026-11-26, debug watch app, 08:56–21:32)

`wear-debug-report.py` over 12.6 h on Matt's wrist (Pixel Watch 5, API 37, batching profile
`hub-40s`, heart rate every 10 s from the raw sensor, Google Health running alongside with a
consumer account):

- **Batches arrive (Q3).** Every recorder's registration fits its FIFO (heart rate wake-up
  3000, accelerometer/pressure/step counter 3000). In doze the app woke ~145 times an hour
  (1826 flush cycles, mean 70 ms awake each, one timeout), i.e. the hub delivered about every
  25 s, sooner than the 40 s latency asked for; no batch was lost for heart rate.
- **No heart rate freeze beside the stock stack (Q4).** 744 worn minutes, 22,535 heart rate
  events (~30 a minute from the 1 Hz registration); no worn stretch longer than 2 min without
  an event except 09:01–09:14, which was the probe/setup window. Google Health kept writing its
  own per-minute heart rate to Health Connect over the same hours, so neither reader froze.
- **Drain** (on battery, between snapshots): doze 2.24 %/h (10.7 h), screen off but awake
  4.9 %/h, screen on 20 %/h; 100 % → 64 % over the day. Wireless debugging was on for part
  of it.
- **Accelerometer completeness is partial**: 13,447 readings/h in doze, 16,927 dark,
  18,512 screen-on, against 22,500 for whole minutes — about 60 % in doze. Matters for the
  sleep estimate (Q8); not yet explained (non-wake sensor, FIFO not overflowing on paper).
- Skin tracker 0 events: the watch was still running the pre-M1 build (installed 09:22).

## What ordinary apps reach on Pixel Watch (reported, not verified here)

Through the raw sensor door:

| Sensor | Evidence | Date |
|---|---|---|
| `android.sensor.heart_rate` (processed bpm) | commercial HR apps list Pixel Watch as supported (Pulsoid FAQ); apps rebroadcast the watch's heart rate over BLE, which needs the sensor API (Heart for Bluetooth on Play); the 2022 contention thread confirms third-party reads happen at all | 2022-12, 2026 |
| `android.sensor.step_counter`, `step_detector` | Wear OS faces and apps read steps on Pixel Watch through the standard API | 2022-11 |
| accelerometer, gyroscope, rotation vector | AOSP types, needed by every Wear OS motion app | — |
| barometer, GPS | hardware is present (altimeter/barometer and dual-frequency GPS in the model's specs); sensor-API access untested | 2026-08 |
| off-body detector | present on Wear OS watches generally; whether the low-latency variant exists on this hub is untested | — |

Through Health Services: the API is documented for third-party apps on Wear OS 3 and up, and Google
named the original Pixel Watch among its supported devices at launch (Android Developers blog,
2022-11). The set of data types a *Pixel Watch 5 on Wear OS 7* exposes to an app that has not been
granted anything by the vendor app is exactly what step 2 measures. The API has been alpha through
its whole life, with types added and removed between versions (the samples repository says so
outright), so a capability query at run time is the only safe design.

## What is closed to ordinary apps

The red/infrared SpO2 light, raw PPG and beat intervals, ECG electrodes and skin conductance have
no open sensor path on Pixel Watch (verified for PPG, ECG and skin conductance by the capture
above; skin temperature, once listed here, turned out to be open), the same as Samsung's `SSENSOR` gate on
Galaxy. Google's own issue tracker has an open, unanswered request to expose beat intervals and
raw PPG through Health Services. There is no third-party Binder route to Google's health stack
either, unlike Samsung's platform: a Pixel metric that is gated at the sensor stays gated at the
sensor.

That sentence has a narrower reach than it used to. Raw light is not the only route to a number:
Health Services is a documented route to the *processed* version of several of these metrics
(spot SpO2, skin temperature, respiratory rate, HRV) on devices that support them. Defeating a
vendor gate stays out of scope, as it does for Samsung. Asking an API Google published for
third-party apps does not.

## What follows for each wanted metric

| Metric | Route on Pixel Watch | In OpenWatch |
|---|---|---|
| Heart rate, steps, ascent | Raw sensors, the same code as on Galaxy. **Verified 2026-11-26:** a day of wear with no contention freeze, batches arrive, and sync lands heart rate and steps in Health Connect (M0). Health Services stays the alternate route | built on raw, **works on Pixel** |
| Sleep stages | From heart rate and motion (`SleepStageEstimator`); Health Services may offer the vendor's own sleep on a device that supports it | built, unverified on Pixel |
| HRV (RMSSD), respiratory rate | Needs beat intervals. **Verified closed on Pixel Watch 5 (2026-11-26):** no `TYPE_HEART_BEAT`, raw PPG signature-gated, `heart_rate_extended` carries no intervals, and Health Services lists no public HRV/respiratory type (the private ones need `OEM_OPAQUE_DATA_ACCESS`) | degrades off by the capability gate |
| SpO2 | **Verified closed (2026-11-26):** no SpO2 sensor listed; no public Health Services type | degrades off |
| Skin temperature | **Open at the sensor** (`com.google.sensor.skin_temperature`, `READ_SKIN_TEMPERATURE`, verified 2026-11-26); Health Services also has a type where a device supports it | the watch app reads skin temperature only from the Samsung tracker today; a Pixel route needs code |
| Calories, distance, floors | No route from the raw sensors OpenWatch reads; **Health Services delivers them to an ordinary app (verified 2026-11-26, consumer-account caveat)** | not built |
| ECG, body composition, skin conductance | Gated | not built |

On the raw path alone, no code change is expected for metric coverage: `WatchSensors` already
offers a metric only where its sensor or source exists, so a Pixel Watch records heart rate, steps,
ascent, sleep and workouts and quietly drops the rest. What the survey can change is the batching
profile (step 3 above) and, if the contention test fails, the heart rate duty cycle.

If a Health Services source is added, that changes the picture rather than the fallback: it is a
*new source*, not a new sensor read, and the honest shape of it is the shape the Samsung Binder
client already uses — its own package, opt-in, capability-probed, and never a silent second writer
beside the raw recorder. That also means it collides with an existing invariant, so it is a
decision to make out loud rather than in a commit: `AGENTS.md` says the watch app reads raw
Android sensors and that "Health Services and the Samsung Health Sensor SDK are not dependencies",
and its stated reason is in [samsung-health-and-privacy.md](../engineering/samsung-health-and-privacy.md).
The Samsung rule is about a proprietary `.aar` and a closed platform; Health Services is a Jetpack
library and a published API, which is an argument that the rule may want splitting rather than
dropping. Whether upstream accepts that argument is upstream's call, and the fork exists so the
question can be asked without holding the family's watches hostage.

## Onboarding, for the record

A Pixel Watch can be installed over adb without the vendor's phone app if the watch's welcome
screen opens Settings before onboarding: reported yes for Pixel Watch by the Gadgetbridge
community and pixelbridge, not verified by this project. Sideloading is beside the point for a
watch that is already set up and paired, which is the case that matters here: wireless debugging
plus `scripts/wear-install.sh` is the whole procedure.

A Google **Workspace** account works for the watch's own setup with the domain's Device Management
turned off (Google's own setup page) — **verified working on this project's watches**, which removes
the usual advice to fall back to a personal account. OpenWatch itself needs no account of any kind,
signed in or out.

## Open questions

1. Health Services answers and delivers on a watch where the vendor app is signed in on a
   consumer account (verified 2026-11-26). **Still open:** does it on a Workspace-only watch, or
   with the vendor app signed out?
2. ~~The full sensor list with permission gates~~ — **answered 2026-11-26**: heart rate is gated by
   `READ_HEART_RATE` only (on Android 17); see the verified capture above. New follow-up: what do
   `heart_rate_extended` and `step_cadence` carry in their event values?
3. ~~Does the Pixel hub hold the `hub-40s` batches?~~ — **answered 2026-11-26: yes**, delivered
   about every 25 s in doze; see "a day of wear" above. Follow-up: why the accelerometer is only
   ~60 % whole in doze.
4. ~~Does batched heart rate beside the stock stack freeze either reader?~~ — **answered
   2026-11-26: no** over 12.6 h (consumer-account watch; repeat once on a Workspace-only watch).
5. ~~Is `TYPE_HEART_BEAT` present?~~ — **answered 2026-11-26: no.** HRV on the raw path stays off
   unless `heart_rate_extended` turns out to carry beat intervals.
6. ~~Which Health Services data types does a Pixel Watch 5 list?~~ — **answered 2026-11-26**:
   see the two verified Health Services sections above; calories, distance and floors appear,
   the vitals do not.
7. ~~Do the stock face's complication slots take OpenWatch's providers, and do the tiles
   appear?~~ — **answered 2026-11-26 (Matt, on the wrist): yes.** The heart rate and steps
   complications work on the stock Pixel face, and OpenWatch's tiles are in the tile list.
   Not checked one by one: the third provider and every slot type (short text, ranged
   value).
8. Samsung's bedtime mode has no Pixel equivalent. Does the Pixel's own bedtime/sleep mode, or
   Do Not Disturb, keep the accelerometer whole enough at night for the sleep estimate to find the
   night, the way the Galaxy section of this map had to learn it did not?
