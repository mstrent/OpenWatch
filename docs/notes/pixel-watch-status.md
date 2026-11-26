# Pixel Watch support — project status & memory

Living document for this fork. Keep it current: any AI assistant or person working
on the Pixel Watch effort reads this **first** and updates it **before finishing a
session** (journal entry + changed status lines), then pushes. It is the durable
memory across assistants and machines; chat history is not.

Branch: `pixel-watch-support` (tracks `origin/pixel-watch-support`).
Upstream: `Hale-MTU/OpenWatch`. Fork: `mstrent/OpenWatch`.
Sensor facts and open survey questions: [research doc](../research/pixel-watch-sensor-map.md)
is the single source of truth for those — do not fork facts into this file.

## Goal

Make OpenWatch work reliably on the family's **Pixel Watch 5 + Pixel phone**, so
that at minimum **steps and heart rate land in Health Connect** (readable by
Strava and Zepp) and are **visible on the watch face**, even when Google Health /
Fitbit is absent or not signed in. Nice-to-have: expose as much Pixel Watch sensor
data as an ordinary app can actually reach.

## Status (as of 2026-11-26, after the M1/M2 implementation pass)

- **Phase: M1 (Pixel skin temperature) and M2 (install ergonomics) are implemented and
  locally gate-verified, committed and pushed on 2026-11-26** (journal below). What is
  left for M1/M2 is the on-watch bench check. The on-device survey continues (steps
  3–4 collecting).
- Survey steps 1–2 **done** on Matt's Pixel Watch 5 (sensor list; Health Services via
  dumpsys and the on-watch probe). Steps 3–4 (all-day batching, HR contention) are
  **collecting**: the debug watch app was left recording on Matt's wrist on 2026-11-26.
- **Feasibility verdict: the minimum bar is reachable** with what an ordinary app gets
  (raw heart rate + step counter, unchanged OpenWatch code paths), pending the all-day
  data. Beyond the bar: skin temperature is newly reachable (raw sensor; OpenWatch now
  reads it, bench check pending);
  calories/distance/floors/running dynamics are reachable through Health Services
  (needs code + the dependency). **SpO2, HRV and respiratory rate are not reachable on
  Pixel** by any route that fits the Workspace-only rule (see route verdicts).
- Caveat on every Health Services result so far: Google Health is signed in on the test
  watch **with a consumer Gmail account**. Results must be repeated on a Workspace-only
  watch (Decisions log) before they count.
- Code changes need Matt's approval before implementation; the milestone plan is
  under "Development plan".

## Route verdicts (summary; evidence in the research doc)

| Route | Verdict |
|---|---|
| Raw sensors: heart rate, step counter/detector, barometer, off-body, accelerometer | **Open**, AOSP permissions, verified delivering |
| Raw `com.google.sensor.skin_temperature` | **Open** behind `health.READ_SKIN_TEMPERATURE`, real °C ~every 30 s; now OpenWatch's skin source on Pixel (M1, bench check pending); the Samsung path stays where present |
| Raw `heart_rate_extended`, `step_cadence` | Open but uninteresting: bpm + status; cadence `-1` at rest. **No beat intervals** |
| Raw PPG, ECG, GSR, 50/100 Hz IMU | **Closed**: `com.google.pixelwatch.permission.READ_PRIVATE_SENSORS` (signature) |
| `TYPE_HEART_BEAT` | **Absent** |
| Health Services public types (HR, steps, distance, calories, floors, elevation, sleep state, exercise metrics) | **Open and delivering** to our app — consumer-account caveat above |
| Health Services `device_private.<n>` types (where Google's vitals likely live) | **Closed**: needs `OEM_OPAQUE_DATA_ACCESS`, `signature\|privileged` |
| Google Health (ex-Fitbit) app → Health Connect; Google Health cloud API | **Excluded** by the Workspace-only rule (needs a Google Health account; Workspace reported unsupported). Cloud API also breaks no-`INTERNET` |
| Health Connect itself | Fine: on-device, no account needed; OpenWatch's phone app already writes to it |

## Decisions log

- **2026-11-26 — Workspace accounts only; no consumer Gmail account required.** Every
  route we build must work for a family member whose only Google account is
  Workspace. Matt's test watch *also* carries a consumer Gmail account (secondary,
  visible to the Google Health app), so a result seen on it can depend on that
  account: a finding only counts once it holds without the consumer account (a
  Workspace-only watch, or Google Health signed out). This rules out, as the plan's
  backbone, routes that need the Google Health (ex-Fitbit) app or its cloud API.
- **2026-11-26 — Upstream acceptance is not a goal.** The fork keeps what upstream
  would reject (e.g. a Health Services dependency); upstream PRs are optional. The
  `AGENTS.md` fork section still says what would need dropping for a PR.
- **2026-11-26 — Google proprietary APIs/libraries are allowed** when they unlock
  sensors or data, subject to the Workspace-only rule and the no-`INTERNET` watch
  invariant (Health Services client 1.1.0 checked: no network permissions).
- **2026-11-26 — Fork is the working copy.** All Pixel work happens on
  `mstrent/OpenWatch` branch `pixel-watch-support`. Ephemeral sandbox checkouts are
  not trusted: the 2026-11-25 tree at `/tmp/OpenWatch` was lost with its docs.
- **Workspace accounts are verified working** for the watch's own setup (domain's
  Device Management off). Do not relitigate personal-vs-Workspace advice.
- Sideloading / onboarding bypass is **historical**: `scripts/wear-install.sh` over
  wireless debugging is the whole install procedure for the family's paired watches.

## What is verified vs. assumed

- **Verified on Matt's Pixel Watch 5 (2026-11-26)**: the sensor list and gates; Health
  Services' capability table (dumpsys) and what it delivers to our app (probe, worn,
  120 s); the private-type refusal; `heart_rate_extended`/skin temperature payloads;
  the debug app's recorders registering with `hub-40s` batches that fit the FIFOs.
- **Verified by Matt (household)**: Workspace-account watch setup works.
- **Verified elsewhere in this repo**: what `docs/research/wear-sensor-map.md` and
  `wear-ppg-hrv.md` mark verified (TicWatch Pro 5, Galaxy Watch8).
- **Reported, not verified here**: Workspace accounts unsupported in Google Health
  (community threads); Google Health app's Health Connect write set.
- **Untested** (research doc open questions 1, 3, 4, 7, 8): Health Services on a
  Workspace-only watch; hub batch delivery over a day; HR contention over a day;
  stock-face complication slots and tiles vs Wear Widgets on Wear OS 7; night
  accelerometer under Pixel bedtime mode.

## Working on this: setup and commands

Machine setup (done once per machine; verified on Matt's Fedora laptop):

- `git submodule update --init --recursive` (`health-calculations`, `openlink`).
- Android SDK: command-line tools in `~/Android/Sdk/cmdline-tools/latest`, then
  `sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"` (the
  package is `android-37.0`, not `android-37`). `local.properties`:
  `sdk.dir=/home/<user>/Android/Sdk` (gitignored). Export `ANDROID_HOME` for scripts.
- JDK: any recent JDK launches Gradle; the foojay resolver downloads JDK 17 into
  `~/.gradle/jdks` itself. No `sudo` needed.
- Dependency changes: `./gradlew :wear:dependencies --configuration debugRuntimeClasspath
  --write-locks --write-verification-metadata sha256`, then **revert lockfile churn in
  unrelated modules** (that command rewrote a dozen of them) and keep only the
  module you changed plus `gradle/verification-metadata.xml`.

The watch:

- Developer options on Wear OS 7: Settings → System → About → Versions → tap Build
  number 7×. Then Developer options → Wireless debugging → Pair new device.
- `scripts/wear-pair.sh <ip>:<pairing-port> <code>`, then `adb connect <ip>:<port>`
  (connect port ≠ pairing port; `adb mdns services` lists both). Matt's watch was at
  `10.0.0.48:35925` on 2026-11-26; the port changes on reboot.
- Install + grant everything: `scripts/wear-install.sh --serial <ip>:<port>` (now also
  grants `ACTIVITY_RECOGNITION`, skin temperature, SpO2). Reinstalling keeps the
  debug app's recorded data.
- Day-of-wear report: `python3 -I scripts/wear-debug-report.py --serial <ip>:<port> --save <dir>`.
  **Turn Wireless debugging off while the watch is worn for data**: recording lives in the
  debug app and needs no adb, and wireless debugging keeps Wi-Fi up, which costs battery
  and skews the report's drain figures. Turn it back on only to pull (pairing persists;
  the connect port changes, `adb mdns services` shows it).
- Survey probe (debug builds only): `adb -s <w> shell am start -n
  tech.mmarca.openwatch.debug/tech.mmarca.openwatch.wear.debug.HealthProbeActivity
  --ei seconds 120`, worn, then `adb -s <w> exec-out run-as tech.mmarca.openwatch.debug
  ls files/debug` and `cat files/debug/probe-<time>.jsonl`. Source:
  `wear/src/debug/kotlin/tech/mmarca/openwatch/wear/debug/HealthProbeActivity.kt`.
  `scripts/wear-install.sh --probe` (debug variant) now starts it and pulls the
  `probe-*.jsonl` files to `reports/` in one shot: default 130 s, override with
  `--probe-seconds <n>`.
- Health Services' own view: `adb shell dumpsys activity service
  com.google.android.wearable.healthservices`. Accounts (don't paste addresses into
  the repo): `adb shell dumpsys account`.

## Next actions

1. **Pull Matt's day of wear** (debug app recording since 2026-11-26 ~09:40):
   `wear-debug-report.py --save`; answer Q3 (do `hub-40s` batches arrive?) and Q4 (does
   HR freeze beside the stock stack?). Ask Matt whether the stock HR ever froze.
2. **Repeat the probe on a Workspace-only watch** (another family watch, or Google
   Health signed out on Matt's). Decides whether the Health Services rows hold.
3. The development plan below: **M1 and M2 are implemented, gated and pushed** (see
   journal), including the phone-side parity follow-up (skin_sensor token + phone skin
   toggle for raw-sensor watches, 2026-11-26) — what is left is the M1 on-watch bench check.
   Matt's OK per milestone still gates what follows: M3 depends on step 1, M4 on step 2.
4. Before any push of code: `./gradlew verifyCi verifyWearCi` and
   `python3 scripts/sync-wiki.py --repo mstrent/OpenWatch --check`. (`verifyWearCi`
   passed on 2026-11-26 with the probe in the debug source set.)

## Development plan (if we start now, from what we know/assume)

Ordered by value and by how little it depends on open questions. Assumptions are
flagged; each milestone ends with an on-watch check, a status-doc update and a push.

**M0 — End-to-end baseline, no code (½ day).** Assumes the existing raw paths just work
on Pixel, which the survey so far supports. Pair Matt's Pixel phone with the debug phone
app (`./gradlew :app:installDebug`, the watch app is the debug build already), add the
watch as a Wear OS watch, sync, and confirm **heart rate and steps land in Health
Connect** and show in Strava/Zepp. Add OpenWatch's tiles and complications to the
watch face (also answers Q7). This proves the minimum bar or finds the real blocker
before anything is built.

**M1 — Pixel skin temperature (small, raw sensor, no new dependency).** Verified route.
**Implemented and locally gate-verified 2026-11-26** (see journal); the on-watch
night-rows bench check remains.
- New `SpotSource<SkinReading>` over `SensorManager` for `com.google.sensor.skin_temperature`
  (non-wake-up is fine while sleep holds the CPU; `values[0]` °C; no ambient channel →
  `ambientCelsius = NaN`/unset, `measured = accuracy >= MEDIUM`), e.g.
  `wear/.../SensorSkinTemperatureSource.kt`.
- Choose it in `WearGraph.kt` (`skinSource`, line ~122) when the sensor exists, Samsung
  otherwise; update the KDoc in `SpotSource.kt` / `SkinTemperatureRecorder.kt` that says
  the sensor is closed (true on Samsung, not on Pixel).
- Permission `health.READ_SKIN_TEMPERATURE` is already declared and requested
  (`WearPermissions.kt`). Phone side already imports skin temperature into Health
  Connect (`devices/wearos/.../WearOsSkinTemperatureImport.kt`) — verify the capability
  `CAP_SKIN_TEMPERATURE` gets advertised once the source exists (`WearLinkServerHost.kt`
  removes it when unavailable).
- Check: a night's skin temperature rows in Health Connect.
- Open detail: delivery is ~every 30 s on-change; confirm it keeps delivering at night
  under bedtime mode (Q8-adjacent).

**M2 — Install/dev ergonomics (tiny).** **Done 2026-11-26**: permissions are granted by
the script, and `--probe [--probe-seconds <n>]` runs `HealthProbeActivity` after install
and pulls the `probe-*.jsonl` files to `reports/` (debug variant only).

**M3 — Hardening, only if the day-of-wear report says so.** If batches drop on the Pixel
hub: a Pixel batching profile or fallback (the old "P2"). If raw HR freezes or goes stale
beside the stock stack: duty-cycle hardening ("P3"), or switch HR to M4's source.
Assumption to test first: with the raw and Health Services readers side by side for two
minutes nothing froze; a day of data decides.

**M4 — Health Services source (medium; depends on the Workspace-only probe).** Assumes the
public Health Services types still deliver on a Workspace-only watch.
- Move `health-services-client` from `debugImplementation` to `implementation` in
  `wear/build.gradle.kts`; re-run the lock/verification update (see setup notes); confirm
  the merged manifest still has no network permission.
- New package `wear/.../healthservices/`, shaped like `wear/.../samsung/`: opt-in,
  capability-probed (`CapabilitiesClient`), never a second silent writer beside a raw
  recorder.
- Value ladder: (a) **all-day calories, distance, floors** — new; needs new
  `WearLinkProtocol` metrics/capabilities in `:wearlink` and phone-side Health Connect
  mapping in `:devices:wearos` (floors already has `WearOsFloorsImport`); (b) **workouts**
  through `ExerciseClient` — Google's calories, distance, pace, running dynamics, GPS — as
  an alternative to the current raw workout path (`wear/.../workout/`); (c) optionally a
  `HeartRateSource` over `MeasureClient`/passive HR beside `SensorHeartRateSource` /
  `SamsungHeartRateSource` if M3 shows contention.
- Decide and record: which source wins per metric when both raw and Health Services exist
  (default: raw stays for HR/steps since it is verified and Workspace-safe; Health Services
  only fills metrics raw cannot).

**Not planned (no route under our constraints):** SpO2, HRV, respiratory rate on Pixel.
They need Google-signed permissions (`READ_PRIVATE_SENSORS`, `OEM_OPAQUE_DATA_ACCESS`) or
the Google Health account/cloud API. `WatchSensors`-style capability gates already keep
them off. Revisit only if Google publishes a type or Workspace accounts become usable in
Google Health. The existing Samsung paths for them stay untouched.

## Conventions for agents (any assistant)

- **Read this file before working; update it before ending the session.** Append
  a dated journal entry (what was done, what was learned, what changed), update
  the Status lines, commit, and `git push` (branch `pixel-watch-support`,
  `origin` = the fork). Never end with unpushed work on an ephemeral machine.
- Upstream rules in the root `AGENTS.md` still apply unless the fork section at
  its bottom overrides them; **do not edit upstream-authored docs to record
  fork-only state** — write fork notes here or under `docs/notes/`.
- Keep facts attributed: *verified here* / *reported elsewhere* / *hypothesis*.
  Do not promote a hypothesis to fact without a capture or Matt's confirmation.
- Commit style: Conventional Commits (`docs:`, `feat:`, `fix:`), message bodies
  carry the why (see repo history). Sign nothing; author as Matt's git identity.
- Treat anything in the watch app's threat model seriously: the no-`INTERNET`
  invariant on `:wear` stays unless Matt explicitly lifts it (Google-API
  permission ≠ network permission, but check what a dependency drags in).

## Session journal (newest first)

### 2026-11-26 — Lobe AI / pair-programmer (session: phone-side parity follow-up)
- **Skin strings vendor-neutral across 15 locales** (`values-{ar,cs,de,es,et,fi,fr,gl,it,iw,ja,pl,pt,ru,zh-rCN}/strings.xml`): `settings_skin_temperature_on` / `settings_skin_explained` no longer say Samsung. SpO2/HRV/HR-source strings **stay Samsung-worded by design** — those routes really are Samsung-only.
- **`reports/` gitignored** — probe output pulled by `wear-install.sh --probe` is personal bench data; no upstream precedent for committed report artifacts. Closes the open question from the M1/M2 journal.
- **`skin_sensor` read-only ability token on the wire**: `WearLinkPreferences.SKIN_SENSOR` ("skin_sensor"), emitted by `WearPreferenceCodec.encode()` as 1/0, documented so an older watch that sends no token reads as OFF. Codec tests assert both values, the absent-token behavior, and that the raw sensor opens skin but never SpO2.
- **Phone skin toggle now gated `samsung || skin_sensor`**: pure seams `skinOfferedByWatch` / `spo2OfferedByWatch` in `WearOsWatchPanelViewModel.kt`; `WearOsWatchPanel.kt` renders the skin SwitchRow under either ability, SpO2 stays inside the Samsung-only block. New VM unit test covers samsung→skin+spo2, skin_sensor-only→skin-not-spo2, neither→neither (5 tests, 0 failures).
- **Gates green (this laptop, pre-commit)**: `verifyWearCi` BUILD SUCCESSFUL (1m9s, ratchets included), `:feature:watches-wearos:testCiUnitTest`, `verifyTranslations` (255 locale files, coverage >70%), wiki link check, merged debug manifest still carries no `INTERNET`/`ACCESS_NETWORK_STATE`/`ACCESS_WIFI_STATE` (grep → 0). Runtimes: launcher JDK 25.0.5, Gradle 9.7.0.
- **Still un-benched**: everything from the M1/M2 pass — real-watch skin cadence, night rows in Health Connect, `--probe` end-to-end.

### 2026-11-26 — Lobe AI / pair-programmer (session: M1 skin temperature + M2 probe, committed)
- **M1 implemented** (now committed; gate verified below):
  - New `SensorSkinTemperatureSource` over `SensorManager` for the raw
    `com.google.sensor.skin_temperature` sensor; `measured` only at accuracy
    ≥ MEDIUM; no ambient channel, so `ambientCelsius = NaN` (skin-only reading).
  - `WearGraph` picks it ahead of the Samsung tracker when the sensor is present,
    Samsung otherwise; `abilities()` reports `skinSensor` without implying Samsung.
  - `SkinTemperatureRecorder` accepts skin-only minutes: the wire has no
    "no ambient" encoding, so absent ambient becomes the in-bounds
    `PLACEHOLDER_AMBIENT100 = 2300`; the phone side maps skin Celsius only, so
    the placeholder never reaches Health Connect. Emitted rows are round-tripped
    through the protocol parser before they are kept.
  - `WatchAbilities.skinSensor` added; the codec now lets the skin setting turn
    on when `samsung || skinSensor`. SpO2 stays Samsung-gated.
  - Settings screen splits the skin row from the Samsung-only SpO2/HR-source
    rows (`samsung` + `skin` availability booleans through the settings port).
  - `HELLO` caps advertise `sk` and `o2` independently (capabilitiesFor seam,
    unit-tested); KDoc/strings across `WearLinkProtocol`, `WearLinkPreferences`,
    `WearSettings`, `WearMetric` and `values/strings.xml` now say Pixel where
    that is true. **Translated `values-*/` strings deferred** — they still say
    Samsung.
- **M2 done**: `scripts/wear-install.sh --probe [--probe-seconds <n>]` starts
  `HealthProbeActivity` after install and pulls `files/debug/probe-*.jsonl` to
  `reports/` (default 130 s; debug variant only — release + `--probe` dies).
  `bash -n` clean; no device run yet.
- **Gate, 2026-11-26 (this laptop, pre-commit)**: `./gradlew verifyWearCi` BUILD
  SUCCESSFUL — includes the FileSize/FunctionLength/LogCallers ratchets. Targeted
  `:wear:testDebugUnitTest` (skin, link-host, source, settings, codec) and
  `:wear:recording-core:test` green. Wiki link check
  (`scripts/sync-wiki.py --repo mstrent/OpenWatch --check`) green. Merged debug
  manifest carries no `INTERNET`/`ACCESS_NETWORK_STATE`/`ACCESS_WIFI_STATE`:
  `grep -cE 'INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE'
  wear/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`
  → 0. Runtimes: launcher JDK 25.0.5, Gradle 9.7.0, `ANDROID_HOME=/home/t3rmin/Android/Sdk`
  exported (the `openlink` included build needs it even though `local.properties`
  has `sdk.dir`).
- **Two gate fixes found during the pass**: the sensor is looked up via
  `getSensorList(TYPE_ALL)` matched on string type — the SDK has no
  `getDefaultSensor(String)` overload; and the source's test uses
  `SensorManager.SENSOR_STATUS_UNRELIABLE` — there is no
  `SENSOR_STATUS_ACCURACY_UNRELIABLE` constant.
- **Still un-benched**: a real Pixel Watch 5 run (skin cadence at night, night rows
  in Health Connect, `--probe` end-to-end) is pending.
- **Deferred / pending Matt's call**: translated `values-*/` `settings_skin_*`
  strings still say Samsung; whether `reports/` (pulled probe output) gets
  gitignored; whether `WearPreferenceCodec.encode()` should emit a `skin_sensor`
  token for the phone UI.

### 2026-11-26 — Claude Code (session: first on-device survey)
- Set up this laptop to build: submodules initialised, Android SDK in `~/Android/Sdk`
  (platform `android-37.0`, build-tools 37.0.0), `local.properties` (gitignored);
  Gradle's foojay resolver provisions JDK 17 itself, so the system JDK 25 is fine.
  `./gradlew :wear:assembleDebug` builds.
- Paired over wireless debugging (`scripts/wear-pair.sh`). Developer options on
  Wear OS 7: Settings → System → About → Versions → tap Build number 7×.
- Watch: Pixel Watch 5 LTE (`wisteria_lte`), build `CP3A.260905.002`, API 37,
  Health Services 0.99.6. Captured the sensor list into the research doc.
- Installed the debug watch app; granted the permissions the script misses; first
  report shows every recorder registered with `hub-40s` batches fitting the FIFOs.
- Learned: skin temperature has an open raw route on Pixel; no `TYPE_HEART_BEAT`;
  `heart_rate_extended` sits behind the public HR grant and is worth probing.
- `scripts/wear-install.sh` now also grants `ACTIVITY_RECOGNITION`, skin temperature
  and SpO2 (verified: revoke, reinstall, all granted).
- Health Services dumpsys: public passive types are HR/steps/distance/calories/floors/
  elevation/sleep detection; **no public SpO2/HRV/resp/skin-temp types**. Fitbit
  subscribes to unnamed `device_private` types. Matt OK'd proprietary Google routes
  in principle (2026-11-26), with the Workspace-only constraint above.
- Wrote `HealthProbeActivity` (debug source set only; Health Services client 1.1.0 as
  `debugImplementation`, no network permissions in its manifest). Worn 120 s run:
  HS heart rate/steps/calories/distance deliver; private HS types need
  `OEM_OPAQUE_DATA_ACCESS` (signature|privileged) — closed; `heart_rate_extended` is
  bpm + status, no beat intervals; skin temperature reads real °C. Details in the
  research doc. Left the debug app recording for Matt's day of wear.

### 2026-11-26 — Lobe AI (session: durable setup)
- Fork `mstrent/OpenWatch` created; clone moved to
  `/home/t3rmin/projects/OpenWatch` with `origin`=fork, `upstream`=Hale-MTU.
- Restored the lost research doc from scratch into the durable clone; committed
  as `f3b2fb5` and pushed `pixel-watch-support`. Wiki check passes.
- Confirmed the complications paragraph in `docs/features/watches.md` is upstream
  text and needs no fork edit; decided the Pixel-specific sentence there waits
  for survey results (no false claims meanwhile).
- Created this status doc, the fork section in `AGENTS.md`, and `CLAUDE.md`.
- Next: on-device survey (needs the watch + wireless debugging).

### 2026-11-25 — Lobe AI (session: sensor-map research, mostly lost)
- Read-only audit of Pixel Watch 5 feasibility: platform facts (Wear OS 7,
  Snapdragon W5 Gen 2 + Cortex-M55 hub, health sensor suite), likely raw-sensor
  reach, likely vendor-gated metrics, no known third-party Binder route to the
  Google health stack (unlike Samsung).
- Drafted the first `pixel-watch-sensor-map.md` and a `watches.md` paragraph in
  an ephemeral `/tmp/OpenWatch` checkout; **lost when the sandbox reset** except
  what was re-derivable. Lesson recorded in Conventions: push or lose it.

### Earlier (pre-fork, context)
- Feasibility discussion established the minimum bar (steps + HR in Health
  Connect, visible on the face) and that stock Pixel health complications cannot
  be fed by third-party apps — watch-face visibility goes through OpenWatch's own
  tiles/complications in editable slots or third-party faces.
