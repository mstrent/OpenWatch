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

## Status (as of 2026-11-27 night: M0 verified, M1 verified on the watch, no-root vitals closed)

- **Phase: M1 (Pixel skin temperature) and M2 (install ergonomics) are done.** M1 verified on
  the watch 2026-11-27: skin rows land, the "Skin temp now" card reads, and the watch's toggle
  works (Matt), and the phone app's "Skin temperature at night" switch appears for the Pixel
  (no SpO2 switch) and drives the watch's setting both ways (2026-11-27 night). **M1 done.**
- **The test watch is Workspace-only since 2026-11-27** (consumer account removed); the Health
  Services offer did not change. **Every no-root route to HRV, SpO2 and respiratory rate is
  now closed on the watch**, including the Samsung-style one (`heart_rate_extended`, re-checked
  at full rate while moving). What remains for those is root, for research only.
- **M0 done 2026-11-26: the minimum bar works end to end.** Debug phone app on Matt's
  Pixel 10 Pro, watch linked (companion association + Validate), 30-min auto-sync and
  manual sync both run; **heart rate and steps are in Health Connect under "OpenWatch"**
  (checked in Health Connect's own Data and access screens). On the watch, the heart rate
  and steps complications work on the stock face and the tiles are listed (Q7).
  **The goal's minimum bar is met on Matt's setup.**
- Survey steps 1–4 **done** on Matt's Pixel Watch 5. Day of wear (12.6 h): `hub-40s`
  batches arrive (Q3), **no heart rate freeze beside the stock stack** (Q4), doze drain
  2.24 %/h; accelerometer only ~60 % whole in doze — a night's data (2026-11-28) shows the gaps
  follow stillness and every sleep minute is recorded; M3 closed without changes.
- **Feasibility verdict: the minimum bar is reachable** with what an ordinary app gets
  (raw heart rate + step counter, unchanged OpenWatch code paths), pending the all-day
  data. Beyond the bar: skin temperature is newly reachable (raw sensor; OpenWatch now
  reads it, bench check pending);
  calories/distance/floors/running dynamics are reachable through Health Services
  (needs code + the dependency). **SpO2, HRV and respiratory rate are not reachable on
  Pixel** by any route that fits the Workspace-only rule (see route verdicts).
- The consumer-account caveat on Health Services results is lifted for the capability table
  and refusals (same on the Workspace-only watch, 2026-11-27). Data *delivery* through Health
  Services on the Workspace-only watch: the 2026-11-27 night probe got passive and measured
  heart rate; calories/distance/floors delivery not re-checked yet.
- Code changes need Matt's approval before implementation; the milestone plan is
  under "Development plan".

## Route verdicts (summary; evidence in the research doc)

| Route | Verdict |
|---|---|
| Raw sensors: heart rate, step counter/detector, barometer, off-body, accelerometer | **Open**, AOSP permissions, verified delivering |
| Raw `com.google.sensor.skin_temperature` | **Open** behind `health.READ_SKIN_TEMPERATURE`, real °C ~every 30 s; now OpenWatch's skin source on Pixel (M1, bench check pending); the Samsung path stays where present |
| Raw `heart_rate_extended`, `step_cadence` | Open but uninteresting: the public bpm + status (re-checked at full rate while moving, 2026-11-27); cadence = steps/min walking, `-1` at rest. **No beat intervals** |
| Raw PPG, ECG, GSR, 50/100 Hz IMU | **Closed**: `com.google.pixelwatch.permission.READ_PRIVATE_SENSORS` (signature) |
| `TYPE_HEART_BEAT` | **Absent** |
| Health Services public types (HR, steps, distance, calories, floors, elevation, sleep state, exercise metrics) | **Open and delivering** to our app — consumer-account caveat above |
| Health Services `device_private.<n>` types (where Google's vitals likely live) | **Closed**: needs `OEM_OPAQUE_DATA_ACCESS`, `signature\|privileged` |
| Google Health (ex-Fitbit) app → Health Connect; Google Health cloud API | **Excluded** by the Workspace-only rule (needs a Google Health account; Workspace reported unsupported). Cloud API also breaks no-`INTERNET` |
| Health Connect itself | Fine: on-device, no account needed; OpenWatch's phone app already writes to it |

## Decisions log

- **2026-11-27 — No fork to maintain: upstream everything, then use the published app.**
  Matt: "I don't want to maintain the fork. I want to push upstream, then use the app they
  publish." **Supersedes "Upstream acceptance is not a goal" below.** Consequences: every change
  must be one upstream would plausibly accept (raw sensors by default, sourced formulas in
  `health-calculations`, no vendor library in release builds, see `AGENTS.md`); the fork branch is
  a staging area for upstream PRs, not a product. `health-calculations` and `openlink` are
  submodules whose only remote is upstream — changes there go through a fork of that repo + PR,
  never a local commit the OpenWatch fork points at.
- **2026-11-27 — M4 (all-day calories and distance) dropped.** Upstream derives distance in
  OpenVitals ("Distance from steps", user-entered stride; height rule judged unsourced), which
  Matt does not want; OpenWatch already writes cadence-based active calories. The design
  (`docs/superpowers/specs/2026-11-27-calories-and-distance-design.md`) and plan are kept for the
  record only. Heart-rate all-day calories would be an upstream proposal with sources, if ever.

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
  Upstream's rule is **not** "raw sensors only" (corrected 2026-11-27): raw sensors
  are the default and vendor libraries stay out of the build, but upstream already
  ships an opt-in, device-gated clean-room client to Samsung's proprietary Health
  Platform (heart rate, IBI, SpO2, skin temperature). Upstream excluded Health
  Services because on the Galaxy Watch it added nothing over raw sensors (it is
  Samsung's tracker underneath) and it carries Play Services telemetry; neither
  reason was measured on a Pixel. An opt-in, Pixel-only Health Services source would
  have that Samsung precedent if a PR is ever attempted.
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
- **Untested** (research doc open questions 1, 3, 4, 7, 8): Health Services calories/distance/
  floors delivery on the Workspace-only watch; HR contention over a day on it;
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
  --ei seconds 120` (add `--ez fastest true` for raw sensors at full rate; a per-slot summary
  of every event is written at the end), worn, then `adb -s <w> exec-out run-as tech.mmarca.openwatch.debug
  ls files/debug` and `cat files/debug/probe-<time>.jsonl`. Source:
  `wear/src/debug/kotlin/tech/mmarca/openwatch/wear/debug/HealthProbeActivity.kt`.
  `scripts/wear-install.sh --probe` (debug variant) now starts it and pulls the
  `probe-*.jsonl` files to `reports/` in one shot: default 130 s, override with
  `--probe-seconds <n>`. Caveat (2026-11-27): the script only *waits* that long; it does not pass
  `seconds` to the activity, which then runs its default 90 s. Needs the **debuggable** build
  (plain `assembleDebug`); on the fast build `run-as` fails and nothing is pulled.
- Health Services' own view: `adb shell dumpsys activity service
  com.google.android.wearable.healthservices`. Accounts (don't paste addresses into
  the repo): `adb shell dumpsys account`.

Emulators (set up 2026-11-27; a functional bench, never a performance or Pixel-hub one —
see the emulator design gate in the 2026-11-26 Lobe AI journal entry). **Current pair: Wear OS
7.0 watch + Android 17 phone**, matching the Pixel Watch 5 / Pixel phones:

- **Command-line tools ≥ 23.0 first** (`sdkmanager "cmdline-tools;latest"`). Tools 19.0
  created API 37 AVDs with `target=android-0` (check `~/.android/avd/<name>.ini`), and those
  boot-loop: SurfaceFlinger aborts on `!rcEnc->featureInfo()->hasReadColorBufferDma`, or the
  emulator itself segfaults in its RenderThread. No GPU/feature flag fixed it; recreating the
  AVD with new tools did. (Old tools kept in `cmdline-tools/19.0-old`.)
- Images: `system-images;android-37.0;android-wear-signed;x86_64` (Wear OS 7.0; the Wear OS
  6+ images carry the `android-wear-signed` tag, not `android-wear`) and
  `system-images;android-37.0;google_apis;x86_64`. AVDs: `ow_watch7` (`wearos_large_round`)
  and `ow_phone17` (`pixel_9_pro`), both `hw.ramSize=4096` (API 37's documented minimum),
  everything else default. Boot: `emulator -avd ow_watch7 -port 5554 -no-snapshot-save` and
  `emulator -avd ow_phone17 -port 5556 -no-snapshot-save`. The "suggested 16 GB host RAM"
  popup is advisory. Two API 37 AVDs take ~8 GB; stop Gradle daemons first on a 16 GB host.
- The watch image is a `user` build (signed, no root, no `sqlite3`): seed its store by
  `run-as <pkg> cat databases/metrics.db` out, edit locally with Python sqlite (checkpoint the
  WAL, `journal_mode=DELETE`), force-stop the app, delete `-wal`/`-shm`, and `adb exec-in
  run-as <pkg> sh -c 'cat > databases/metrics.db'` back. Table `rows`: `metric, time_ms,
  line, seq`. A tile keeps its read for 5 min; `DEBUG_SURFACE remove-tile`/`add-tile` (and
  `DEBUG_SYSUI show-tile`) forces a new one.
- Complications: `DEBUG_SURFACE set-complication` works on Wear OS 5.1 faces (Utilita) but
  not on Wear OS 7's declarative faces ("Watch face package is not installed"); add by hand.
- **The real link works**: netsim carries classic Bluetooth + RFCOMM. Watch discoverable
  (`am start -a android.bluetooth.adapter.action.REQUEST_DISCOVERABLE`), pair in phone
  Settings → Pair new device, phone OpenWatch → Pair a watch → Allow (companion), then on the
  watch OpenWatch → Settings → Phone → Allow (the notification's buttons ignore emulator
  taps). Phone grants: `pm grant` each `android.permission.health.*`, `dumpsys deviceidle
  whitelist +<pkg>`. `uiautomator dump` + tap-by-text beats coordinates.
- Limits: neither watch image has a skin temperature sensor, so the watch does not announce
  `sk` and skin sync cannot be exercised end to end. The older Wear OS 5.1 / Android 16 pair
  (`ow_watch`, `ow_phone`) is still installed and boots with defaults; its watch is userdebug
  (root + `sqlite3`).

## Next actions

0. **Upstream the fork's code (new top priority, 2026-11-27).** Candidates on this branch
   (`git log upstream/main..HEAD`): Pixel raw skin temperature + skin_sensor token + phone toggle;
   skin "now" card, variation tile/complication; settings-flow GC fix; tile caption wrap;
   wear-install fixes (permissions, pipefail, compile sideloads), jank capture script. Fork-only,
   not for upstream: the debug Health Services probe (vendor library, even debug-only), these
   notes. Sort into small PRs against `Hale-MTU/OpenWatch`, each re-checked against `AGENTS.md`.
   Split decided 2026-11-27: PR 2 install script — **open as #96**; PR 3 Pixel raw skin temperature +
   skin_sensor token + phone toggle (after tonight's on-wrist check); PR 4 all-day skin, "now" card,
   variation tile/complication + caption wrap — waits for the variation calculation to move into
   `health-calculations` (its own PR there, via a fork of that repo). Not upstream: settings GC fix
   (upstream #83), the Health Services probe, the jank script (for now).

1. ~~Finish M1~~ — done 2026-11-27 night (phone skin switch verified). Strava/Zepp: Strava only reads workouts from Health Connect, so nothing
   to see until a workout is recorded; Zepp not checked.
   Note: Google Health also writes the watch's steps/HR to Health Connect, so with both apps
   present Health Connect's app priority decides which counts (total did not double: 3,532).
2. **Vitals beyond skin temperature (HRV, SpO2, respiratory rate): no no-root route is left**
   (2026-11-27 night). Decide: root the test watch for research only (bootloader unlock wipes
   it; even then an ordinary app cannot read the private types, so it would not ship to the
   family) — or stop here and put the effort into M3/M4. Recommended: stop.
   Cheaper research step first (no wipe): an overnight vendor-log capture (2026-11-27 late
   evening journal); the vendor log already names the private types.
3. The development plan below: M1 and M2 done. Matt's OK per milestone gates what follows:
   **M3** — **closed 2026-11-28, no hardening needed** (one night: every minute recorded; the
   accelerometer gap follows stillness) — and **M4**
   ~~(calories/distance)~~ — dropped 2026-11-27, see Decisions log. Still owed on the Workspace-only watch: a
   day-of-wear repeat of the HR contention check (question 4).
4. Before any push of code: `./gradlew verifyCi verifyWearCi` and
   `python3 scripts/sync-wiki.py --repo mstrent/OpenWatch --check`. (`verifyWearCi`
   passed on 2026-11-26 with the probe in the debug source set.)
5. **Dashboard jank — attributed and fixed for sideloads (2026-11-26 evening; journal).**
   Cause: the **debuggable** build, not the UI code. ART runs a debuggable app
   interpreted (simpleperf: 78% of the main thread in `ExecuteSwitchImplCpp`, framework
   included) and refuses to compile it; a non-debuggable build sat JIT-only after
   `adb install` until overnight dexopt. `wear-install.sh` now compiles non-debuggable
   installs (`speed-profile` from the shipped baseline profile) and warns on debuggable
   ones. Remaining, optional: the crown/rotary baseline; a generated app baseline profile
   (`:wear:benchmark`) only if fresh Play/nightly installs prove janky — the library
   profiles already ship in release APKs. **Never judge smoothness on a debuggable build.**
   Trade-off: `DebugProtocol` and `run-as` (so `wear-debug-report.py`) need the debuggable
   build; install it only for survey days.

## Development plan (if we start now, from what we know/assume)

Ordered by value and by how little it depends on open questions. Assumptions are
flagged; each milestone ends with an on-watch check, a status-doc update and a push.

**M0 — End-to-end baseline, no code (½ day).** **Done 2026-11-26**, Q7 included (see
Status). Originally: assumes the existing raw paths just work
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

### 2026-11-28 (morning) — Claude Code (session: the night's report, M3)
- `wear-debug-report.py` + the watch's `metrics.db` over the night (bedtime 22:00–07:55, synced
  hold all night, no wake lock). Details in the sensor map, "a night of wear".
- **M3 verdict: no hardening needed.** Every minute of the night has a sleep row and heart rate;
  no batch lost. The accelerometer thins out while the wrist is still (median 196 of ~378
  readings a minute, 14 % of minutes empty, each isolated); the gaps follow stillness, and
  movement is already normalised per reading, so the sleep input holds. Bedtime drain 1.69 %/h.
- **Skin fixes verified on the watch:** night rows only inside the hold (114), day rows only
  outside it (159). Still to check on the phone: last night's sleep estimate, and the night skin
  rows in Health Connect. Then PR 3.
- Watch still has the debuggable build.
- **Phone side (adb on the phone):** sleep written to Health Connect, one night 23:42–07:24
  (430 min asleep, 32 awake, window by angle) — Matt to confirm the times. Skin night rows
  reached the phone (night median 33.41 °C from 78 minutes); no Health Connect skin record yet,
  by design: upstream's baseline needs 3 other nights first.
- **Found a PR 3 blocker:** the Pixel skin sensor is on-change at ~0.2 °C steps, so on a steady
  night it is silent for up to 50 min (values either side of every gap differ by one step).
  The phone's rules (`WearOsSkinTemperatureImport`: MAX_GAP 30 min, SETTLE 30 min) read that
  as the watch coming off: the night split into 3 runs and one closed early. The minute median is
  also over change-minutes, not time. Proposed fix (awaiting Matt): on the watch, repeat the last
  value each worn minute (sample-and-hold), so the phone's rules and Samsung's path stay as they are.

### 2026-11-27 (night, 2) — Claude Code (session: review + first upstream PR)
- **Upstream PR opened: [Hale-MTU/OpenWatch#96](https://github.com/Hale-MTU/OpenWatch/pull/96)**
  — `wear-install.sh` grants ACTIVITY_RECOGNITION/skin/SpO2, compiles non-debuggable sideloads,
  pipefail-safe matching. Cut from `upstream/main` as branch `upstream/wear-install-grants-compile`
  on the fork, without the fork-only `--probe`. Verified on the watch.
- Upstream already fixed the settings-after-GC bug themselves (#83): our `f195510` is dropped
  from the upstream list. The tile-caption wrap (`93c656a`) has no upstream-visible failure on its
  own (their longest caption is 24 chars) — it rides with the skin variation tile PR.
- **Whole-branch code review** (`/code-review high`) found 3 important + 7 nits, no crashes.
  Fixed test-first and pushed (`a43e1df`, `2190e7c`, `e5209be`): skin minutes written once as the
  mean (SkinMinuteBuffer), routed by their own time against the hold windows (SkinHoldWindows),
  FIFO points no longer dropped as stale (only the pre-registration cached reading), skin_sensor
  means the raw sensor only, Skin-now card only on all-day watches and kept with the week (day
  series kept a week), phone switch says "all day" on a raw-sensor watch. Not fixed: the watch's
  SkinVariation differs from the phone's baseline rules (belongs in shared `health-calculations`
  before its PR), the jank script's median (script not going upstream yet).
- `verifyCi verifyWearCi`, translations and wiki check green. Fixed builds installed on watch
  (debug, for tonight's report) and phone at 20:03.
- **Tonight (M3 + the fixes):** Matt wears the watch to bed. Tomorrow: `wear-debug-report.py`
  for hold coverage and accelerometer completeness during sleep; skin night rows synced, day rows
  watch-only; then open the Pixel skin temperature PR (PR 3 in Next action 0).

### 2026-11-27 (late evening, 2) — Claude Code (session: ECG under the vendor log)
- **Matt re-added the consumer Gmail account** so the Fitbit ECG app would run. The test watch is
  **no longer Workspace-only**; Workspace-only findings above stay valid as recorded, but new
  Health Services results from now on carry the consumer-account caveat again.
- ECG turns on airplane mode for the reading, so adb over Wi-Fi drops; captured instead by
  enlarging logd (`logcat -G 64M`), reading after reconnect, then restoring 64K (done). Findings
  in the sensor map: ECG is the ordinary signature-gated `ECG Sensor` (250 Hz) + lead detector;
  hub front end switches to a mixed ECG/PPG op; no result or samples in the log.
- Watch: logd buffer restored to 64 KiB; debuggable OpenWatch build still installed.
- Lesson: keep captures short (Matt: 2 min, not 8–11) and state the duration before starting.

### 2026-11-27 (late evening) — Claude Code (session: vendor log capture)
- Matt turned on **OEM unlocking** (no effect by itself: `ro.boot.flash.locked=1`, verified boot
  green, `user` build, `adb root` refused) and **verbose vendor logging** (useful: the hub firmware
  and Health Services HAL now log to logcat, readable by adb without root).
- Streamed ~9 min of `logcat -b all` worn (rest, walk, Fitbit Exercise walk, rest). Findings in the
  sensor map ("what the vendor log shows"): the private passive types are AZM, CARDIO_LOAD and
  **FITBIT_SITE** — a bundle of hub records (HRM, EDA_CONT_MINUTE, MSA_MINUTE, SLEEP_300S,
  SLEEP_STAGES, GROK); **no SpO2/HRV/respiratory type name anywhere**. Research only — no app route.
- Open question for root/fastboot: how a Pixel Watch 5 reaches fastboot at all (no USB data port
  on earlier Pixel watches; they needed a debug dock). Do not `adb reboot bootloader` until known.
- Next, if wanted: an **overnight log capture** (Wireless debugging on, watch worn to bed) to see
  whether night vitals show up as records, sensors (red/IR PPG) or values.

### 2026-11-27 (night) — Claude Code (session: heart_rate_extended full-rate re-check; docs corrected)
**The Samsung-style HRV lead is closed on Pixel; no no-root route to HRV/SpO2/respiratory remains.**

- Found that the previous session's "unprobed" lead (`heart_rate_extended`, 131086) had been
  probed on 2026-11-26; the sensor-map table was stale. Matt asked for a stronger re-check anyway.
- Probe change (debug source set only): `--ez fastest true` registers the raw sensors at
  `SENSOR_DELAY_FASTEST`, and every run now ends with a `sensor.summary` per sensor over *all*
  events (per slot: min, max, non-zero count, distinct values), not only the first 40 logged.
- Result (`probe-20261127-172506.jsonl`, worn, 324 s: rest, walking, arm movement):
  `heart_rate_extended` = 170 events, same count and bpm range (65–94) as `android.sensor.heart_rate`,
  slot 2 = status (2–3), every other slot zero in every event. The fastest rate does not speed it up
  (~0.5 Hz). It is the public heart rate re-labelled. `step_cadence` walking values seen for the
  first time: steps/min in slot 0 (~90–103), `-1` at rest and in pauses.
- Matt: the watch's skin temperature toggle works. M1 done but the phone toggle check.
- Pitfalls hit: (1) the previous journal gave `OPENWATCH_PUBLISHED_DEBUG_APK=true` as the way to
  get the *debuggable* build; it is the opposite (fixed in that entry). On the fast build the
  probe runs but `run-as` cannot pull its file. (2) Reinstalling the app kills a running probe
  before its summary (first 5-min run lost its summary that way). (3) `wear-install.sh --probe`
  does not pass `--probe-seconds` to the activity (noted under setup; not fixed).
- **Phone skin switch verified (M1 done).** Matt could not find it: the phone's debug app was
  installed 2026-11-26 12:36, before `487c90a` (15:08) added the Pixel gate. Updated it in place
  (`adb install -r app/build/outputs/apk/debug/app-debug.apk`; pairing kept). The watch's card
  then shows "Skin temperature at night" on, no SpO2 switch; flipping it off/on from the phone
  set `skin_temperature` false/true in the watch's `wear_settings.xml` within seconds. Lesson:
  after phone-side changes, reinstall the phone app before asking Matt to look.
- Gates: `verifyCi verifyWearCi` and the wiki check passed (needs `ANDROID_HOME` exported on this machine). Watch left on the
  **debuggable** build (janky); reinstall the fast build when the survey is done. Remind Matt to
  turn Wireless debugging off.

### 2026-11-27 (evening) — Claude Code (session: Samsung-clue analysis; root decision pending)
**All non-root hypotheses for the Pixel vitals are now closed on the real watch; one reachable
sensor remains unprobed; root is the next step and needs Matt's hands.**

- **Vendor-app occupancy: ruled out.** Disabling `com.fitbit.FitbitMobile`,
  `com.google.android.wearable.pixel.aspen` and `com.fitbit.ecg` (`pm disable-user`) and
  restarting Health Services left the passive table byte-identical, capabilities unchanged,
  private ids still refused, HRV-by-name still "not supported". Re-enabled. (commit `1aa5b4f`)
- **Consumer account: ruled out.** Matt removed the consumer Gmail account and rebooted
  (Workspace-only watch now). Same table, same refusals. A stale `clockwork.accounts` record
  of it lingers. (commit `6f06d92`)
- **What the repo's other-watch support teaches (analysis, no code):** the `wear/.../samsung/`
  package is a clean-room Binder client for `com.samsung.android.service.health` that gets HR+IBI,
  SpO2 and skin temp with no SDK and no root (`docs/engineering/samsung-health-and-privacy.md`).
  Two lessons: (1) talk to the system health service directly over Binder — but this does NOT
  port to the Pixel, whose private Health Services types are gated by `OEM_OPAQUE_DATA_ACCESS`
  (signature|privileged), checked by the framework, not the service; a clean-room client cannot
  satisfy it. (2) Samsung's *default* HRV source is not the IBI service but `PpgWindowSource`
  reading raw PPG (`com.samsung.sensor.hr_raw`, 25 Hz) via plain `SensorManager` under the
  ordinary HR permission, fed to the device-agnostic pipeline `PpgBeats -> HrvRmssd -> NightHrv`.
- **Correction (2026-11-27 night): this lead had already been probed on 2026-11-26** (bpm + status,
  no intervals; the sensor-map table still said "payload unknown"). Re-checked at full rate while
  moving the same night — closed. See the next journal entry.
- **The one no-root lead left, reachable NOW (no wipe):**
  `com.google.sensor.heart_rate_extended` (131086), gated by `health.READ_HEART_RATE` — a grant
  the app already holds. Payload undocumented. If it carries beat intervals / confidence / a
  waveform, the existing HRV pipeline consumes it and HRV needs no root. **Not yet probed**
  (would need a small debug-only `SensorManager` listener on type 131086 in `wear/src/debug/`).
  `com.google.sensor.skin_temperature` (65555) open (already used). Raw PPG (65572), PPG
  controller (65549), ECG, GSR all `READ_PRIVATE_SENSORS` (signature) — unreachable without
  root or privileged signing. **No SpO2 and no respiratory sensor type exist on the raw path**;
  those live only inside Google's private streams.
- **Root (research only, not kept — Matt's standing decision):** I cannot do it from here. A
  Pixel Watch 5 on a `user` build needs a physical bootloader unlock (wipes the watch) then
  fastboot-flashing a Magisk-patched boot image. Once rooted, the research targets are: dump the
  private PPG/GSR/skin/ECG sensor payloads; decode the private Health Services ids 65537-65549;
  trace how `pixel.aspen`/Fitbit read them; pull the sensor HAL. Even with root, an *ordinary*
  app still cannot read the private types — root tells us what we're missing and whether any
  no-root shadow (like 131086) carries the same data.
- **DECISION PENDING for next session:** probe 131086 first (no wipe; may yield HRV) vs. root
  now (wipe; only route to SpO2/private streams) vs. both in parallel. I recommend probing 131086
  first — cheap, reversible, and it is the direct application of the Samsung lesson.
- **Watch state:** fast build reinstalled and confirmed NOT debuggable (so `run-as` probes need
  the debuggable build reinstalled first: plain `./gradlew :wear:assembleDebug`, **without**
  `OPENWATCH_PUBLISHED_DEBUG_APK` — that flag makes the *fast, non-debuggable* build; corrected
  2026-11-27 night — then `scripts/wear-install.sh --serial <w>`). Wireless-debugging address changes on reboot
  (`adb mdns services`); remind Matt to turn Wireless debugging off when done.

### 2026-11-27 (late afternoon) — Claude Code (session: vendor-app exclusivity test)
- Watch on a new wireless-debugging address (it changes; use `adb mdns services`).
- **Disabling Fitbit, Connected fitness (pixel.aspen) and Fitbit ECG changes nothing** for an
  ordinary app: same HAL passive table, same 6 public types, private ids refused for
  `OEM_OPAQUE_DATA_ACCESS`, vitals by name refused as unsupported. Re-enabled afterwards.
  Details in the sensor map. Occupancy hypothesis closed.
- Accounts on the watch: the Workspace account is primary (set up first); a consumer Gmail
  account was added after it, so removing it should not force a reset.
- **Removing the consumer account (and rebooting) changes nothing either**: same table, same
  refusals, Workspace-only watch now. A stale `clockwork.accounts` record of the consumer account
  remains. With this, both the occupancy and the account hypotheses are closed; what is left for
  the vitals is root/priv-app (debug/RE only) to read the private ids' formats.
- Fast build reinstalled (not debuggable; `run-as` probes need the debug build again).

### 2026-11-27 (afternoon) — Claude Code (session: settings listener bug; vitals blockers mapped)
- **Bug fixed**: `WearGraph.settings` kept only `WearSettingsState(app).state`; SharedPreferences
  holds listeners weakly, so after a GC the watch's settings screen (and anything reading the
  flow) stopped following writes. Symptom: "Skin temperature" toggle stored `true` but the row
  stayed "off". Regression test `WearSettingsTest` (GC with only the flow held). Verified on
  the watch: recorder registers the skin sensor, `skd` rows land, "Skin temp now" card shows
  91.1 °F. Not yet re-verified by tapping the toggle (adb taps on the dashboard's Settings
  button did not navigate; left for Matt).
- **Vitals blockers on Pixel Watch 5** (dumpsys): Health Services' table comes from the vendor
  HAL ("Platform: OWL", SystemService v8); passive = 6 public types + sleep/abnormal-HR + 11
  private ids 65537–65549. Private ids need `OEM_OPAQUE_DATA_ACCESS` (signature|privileged);
  Fitbit and "Connected fitness" (pixel.aspen) are PRIVILEGED system apps. Raw PPG/ECG/GSR need
  `READ_PRIVATE_SENSORS` (signature|privileged). Fitbit complications: no vitals (Readiness only).
  Watch Health Connect empty. Open options put to Matt: phone Health Connect via Google Health
  (consumer account), root/priv-app + decode private bytes, or disable-Fitbit exclusivity test.
- **Watch left on the debuggable build** (janky); reinstall the fast build with
  `OPENWATCH_PUBLISHED_DEBUG_APK=true ./gradlew :wear:assembleDebug` + `scripts/wear-install.sh`.

### 2026-11-27 (midday) — Claude Code (session: Wear OS 7 vitals through Health Services)
- Matt wants HRV, respiratory rate, skin temperature, SpO2, VO2 max and blood pressure.
  The probe now subscribes to **every type Health Services lists** (passive) and every listed
  measure type (opt-in, see below).
- **Wear OS 7.0 emulator, no Google account: all of them reach an ordinary app** through the
  public API once four ordinary Android 17 runtime permissions are held:
  `health.READ_HEART_RATE_VARIABILITY`, `READ_RESPIRATORY_RATE`, `READ_VO2_MAX`,
  `READ_BLOOD_PRESSURE` (skin temperature and SpO2 use the ones the app already has). Not
  signature-gated, unlike `OEM_OPAQUE_DATA_ACCESS`. Over 120 s: HRV RMSSD/SDNN/pNN50,
  respiratory rate, skin temperature, SpO2 every ~6 s, VO2 max once — **flat synthetic
  values** (20/40/5, 12, 33, 95, 30), so the path is proven, not any sensor.
- **Blood pressure**: the measure registers and data arrives, but `health-services-client`
  1.1.0 (newest on Google Maven) crashes the app decoding it (`ClassCastException
  DataProto$Value$DoubleArray → double[]` in `DataType.toProtoFromValue`), before our callback.
  The probe skips non-HR measure types unless `--ez measureAll true`.
- The permissions are declared in `wear/src/debug/AndroidManifest.xml` only (probe use).
- **Real Pixel Watch 5 (firmware `CP3A.260905.002`, permissions held): still closed.** It
  lists only HR/steps/distance/calories/floors/elevation (passive) and HR (measure); exercise
  adds running dynamics but no vitals. Asking for the vitals by name (`--ez tryUnlisted
  true`) is refused: `HealthServicesException: Requested data type … Heart Rate Variability
  (RMSSD) … not supported by device passive monitoring capabilities`. The watch's own Health
  Connect holds nothing: every connected app shows "Contributed Data: false" and Fitbit is not
  connected to it. **Conclusion: on the Pixel, HRV/respiratory rate/SpO2/VO2 max/BP reach no
  third-party app today** — only Google's private pipeline. Skin temperature stays open via
  the raw sensor (built). Re-run the probe after each watch firmware update.

### 2026-11-27 (afternoon) — Claude Code (session: distribution options + upstream rule wording)
- Discussed distribution for the family. Upstream already publishes
  `tech.mmarca.openwatch` on Play (README pipeline; listing page exists, its track and
  form factors not verified). A fork listing would need its own `applicationId`; the
  likely path is Play internal/closed testing with a family Google Group. Managed
  Google Play private apps were judged a poor fit (no Wear OS enterprise management).
  Merging upstream instead was raised as an option, not decided.
- Corrected the "raw-sensors-only" wording in the `AGENTS.md` fork section and in
  Decisions above: upstream allows opt-in vendor-service sources (its Samsung Health
  Platform client); see `docs/engineering/samsung-health-and-privacy.md`.

### 2026-11-27 (late morning) — Claude Code (session: emulators moved to Wear OS 7 + Android 17)
- Matt pointed out Wear OS 6+ images exist (tag `android-wear-signed`, which my search
  missed). Moved the bench to Wear OS 7.0 + Android 17 to match his devices.
- **Lost time to a tooling bug**: the AVDs boot-looped through several RAM/GPU changes until a
  web search matched our `hasReadColorBufferDma` abort to command-line tools 19.0 writing
  `target=android-0`. Updating to 23.0 and recreating the AVDs fixed it with default graphics.
  Lesson: read the docs/search before tuning flags.
- On Wear OS 7: the skin tile renders "+0.4°" with the same 7 bars; the phone pairs, links
  and syncs over emulated Bluetooth (`Served the phone link: SERVED`).
- **Account-free Health Services on Wear OS 7** (0 Google accounts): measured HR (89 points)
  and passive HR/steps/calories/distance/floors delivered, as on 5.1. **New**: Wear OS 7's
  Health Services *lists* passive HRV (RMSSD/SDNN/PNN50), respiratory rate, skin temperature,
  SpO2 and VO2 max, and a Blood Pressure measure type, all absent on the Pixel Watch 5
  (2026-11-26). So public types for the vitals exist in the platform; the Pixel chooses not
  to offer them (and the emulator likely advertises everything off synthetic data). Delivery
  of those types was not probed (the probe subscribes only to the older set).

### 2026-11-27 (morning) — Claude Code (session: emulator bench)
- Watch + phone emulators set up (commands in "Working on this"). Debug builds of both
  apps installed; the phone paired and linked to the watch over emulated Bluetooth and
  ran a full sync (steps, sleep, hrv, entries, workouts all answered).
- Skin variation checked on the emulator with seeded nights: the tile showed **+0.4°**
  with 7 bars matching a hand computation of each night's rolling baseline; the
  complication showed "+0.4°" with the thermometer icon on Utilita; with two nights the
  tile showed "—" and the building caption. **Bug found and fixed**: that caption was
  ellipsized ("…set your bas…"); every metric tile's caption may now take two lines.
  Not checkable there: the "Skin temp now" card (no skin sensor → not offered).
- **Bug fixed in `scripts/wear-install.sh`**: under `set -o pipefail`, `adb shell … |
  grep -q` read false when grep quit early, so a debuggable build was reported
  "compiled (speed-profile)" and the link-service check could misreport. Both now
  capture the output first.
- **Account-free Health Services (evidence for open question 1, not an answer)**: on
  the stock Wear OS 5.1 emulator with **zero Google accounts**, the probe saw Health
  Services list its passive/measure/exercise capabilities and deliver 89 measured HR
  points and passive HeartRate/Steps/Calories/Distance/Floors over 90 s. So Health
  Services itself does not require any account. It does **not** show what a Pixel
  Watch's vendor-backed Health Services does with Google Health signed out or
  Workspace-only: that still needs the real watch.

### 2026-11-27 (late night) — Claude Code (session: skin temperature now + Google-Health-style variation)
- Matt asked for skin temperature "right now" + graphed (for fun) and a tile/complication
  "like Google Health's". Researched: Google Health shows skin temperature only as the nightly
  variation from a 3-night baseline; the PW4/5 sensor reads all day (our probe saw ~30 s).
  Decided with Matt: daytime readings stay on the watch, never Health Connect.
- Spec `docs/superpowers/specs/2026-11-26-skin-temperature-now-and-variation-design.md`, plan
  `docs/superpowers/plans/2026-11-26-skin-temperature-now-and-variation.md`. Built (commits
  `16b49a4`..`d1e5b99`, `verifyWearCi` green):
  - Raw-sensor watches record skin all day on the wrist (`RecorderPlan.Inputs.skinAllDay`);
    minutes outside a sleep hold go to `WearMetrics.SKIN_TEMPERATURE_DAY` (key `skd`, 2 days,
    **not in `WearMetrics.ALL`**, so never announced or served); held nights stay in
    `SKIN_TEMPERATURE`, now kept a month + 2 days for the baseline. Non-wake-up sensor variant.
  - `SkinVariation` (pure): night = median of a noon-to-noon window's minutes (≥30), baseline =
    median of up to 30 earlier nights once ≥3 — the phone's numbers. °F deltas ×1.8.
  - Dashboard card `MetricTile.SKIN_TEMPERATURE_NOW` ("Skin temp now"), offered where
    `WatchSensors.skinSensor`; value only if the newest minute is ≤15 min old.
  - `SkinTemperatureTileService` (+0.3° vs your baseline, last 7 nights as bars) and
    `SkinTemperatureComplicationService` (SHORT_TEXT, hourly); "N more nights" until a baseline.
  - The skin setting still defaults **off**; its text now says all day on Pixel.
- **Not yet verified on the watch**: adb dropped before install. Next session: enable Wireless
  debugging, `scripts/wear-install.sh` (debuggable, for `run-as`), turn skin on in watch
  Settings → Recording, then check `skd` rows appear by day, a phone sync writes no new skin
  record by day, the card shows a value, and the tile/complication render ("3 more nights").
  Then reinstall the smooth build (`OPENWATCH_PUBLISHED_DEBUG_APK=true` build + wear-install).

### 2026-11-26 (night) — Claude Code (session: dashboard jank attributed)
- `simpleperf record --app tech.mmarca.openwatch.debug -e cpu-clock -g` works on the
  user-build watch (plain `-p <pid>` is refused; `--app` runs it in-app) and
  `simpleperf report` prints on the watch, so no download is needed. During the scripted
  swipes the main thread used 3.6 s of 9 s, 78% of it in libart's switch interpreter
  (`ExecuteSwitchImplCpp`), including `Looper`/`Choreographer`: the whole process ran
  interpreted.
- A/B, same HEAD code, `wear-jank-capture.sh -r 3` each (median janky %, p90):
  debuggable 63%/300 ms; debuggable + `compile -m speed` 42–46%/300 ms (status stays
  `verify`: ART will not compile a debuggable app); non-debuggable
  (`OPENWATCH_PUBLISHED_DEBUG_APK=true`) fresh `adb install` 17%/65 ms; same after
  `compile -m speed-profile` **1.6–2%/22 ms, 0 missed vsync, p50 15 ms**; through the new
  `wear-install.sh` path after `compile --reset` 1.7–2.4%/22 ms (the reset may keep the
  earlier JIT profile, so not a strictly cold install).
- Non-debuggable APKs already carry `assets/dexopt/baseline.prof` (library profiles,
  Compose's included) plus ProfileInstaller 1.4.0; `adb install` just does not compile them.
- `wear-install.sh`: new `compile_app` step (ProfileInstaller `INSTALL_PROFILE`
  broadcast, then `cmd package compile -f -m speed-profile`); warns instead on a
  debuggable install (`pkgFlags` contains `DEBUGGABLE`).
- The watch now runs the **non-debuggable** HEAD debug build (includes M1 skin): the
  protocol recorder is off and `run-as` is gone. Reinstall the debuggable build with
  `scripts/wear-install.sh` (default debug APK) for the next survey day.
  `screen_off_timeout` restored to 10000.

### 2026-11-26 — Lobe AI / pair-programmer (session: dashboard jank baseline captured on Pixel Watch 5)
- **Emulator design gate:** an emulator is valid ONLY as a *relative* comparator — same
  host/AVD/variant, quiesced host, animations off, repeated interleaved runs, compare
  medians/p99 — never means or single runs. It cannot be trusted for absolute ms, Pixel
  Watch GPU behaviour, thermal throttling, vsync cadence, crown/rotary latency, real
  Health Connect/sensor load, or low-RAM pressure. No emulator or system image was
  installed; the real watch superseded that plan.
- **Accepted measurement ladder:** Rung 1 `dumpsys gfxinfo <pkg> framestats` + Perfetto
  on the real watch (primary, zero new code); Rung 2 Compose compiler stability/metrics
  dump; Rung 3 real Pixel Watch 5 over adb (authoritative absolute); Rung 4
  `:wear:benchmark` Macrobenchmark module (right end-state, deferred).
- **Harness:** `scripts/wear-jank-capture.sh` (committed in `a994bad`). Baseline command:
  `scripts/wear-jank-capture.sh -s "adb-68181WRDTL51TA-sO3EOB._adb-tls-connect._tcp"
  -p tech.mmarca.openwatch.debug -r 5 -o reports/jank`
- **Devices attached during capture (both at once, so every adb call needs
  `-s <serial>` or `ANDROID_SERIAL`):** phone `adb-5A160DLCH00311-IatGql._adb-tls-connect._tcp`
  model `Pixel_10_Pro` (`blazer`); watch `adb-68181WRDTL51TA-sO3EOB._adb-tls-connect._tcp`
  model `Pixel_Watch_5` (`wisteria_lte`). Watch display 480x480, density 320,
  vsync 60.000004 Hz, `presentationDeadlineNanos=16000000`, supportedRefreshRates
  [60.000004, 30.000002, 20.0].
- **First run INVALID:** 4 of 5 runs reported `Total frames rendered: 0`. Root cause:
  `mWakefulness=Dozing`, so gfxinfo recorded no frames. Fix: wake with `input keyevent
  224` and raise `screen_off_timeout` from its original `10000` to `600000` (600000 is
  still set on the watch as of this writing — restore it to 10000 when jank work ends).
- **Valid baseline, 5 runs.** Jank artifacts under the gitignored `reports/jank/`
  (`.gitignore:47` ignores `reports/`; artifacts are local-only, NOT committed):

  | run | frames | janky | janky% | p90ms | p95ms | p99ms |
  |---:|---:|---:|---:|---:|---:|---:|
  | 1 | 67 | 31 | 46.27 | 300 | 300 | 400 |
  | 2 | 45 | 30 | 66.67 | 350 | 400 | 400 |
  | 3 | 47 | 31 | 65.96 | 300 | 350 | 400 |
  | 4 | 63 | 29 | 46.03 | 300 | 350 | 400 |
  | 5 | 56 | 36 | 64.29 | 300 | 350 | 400 |

  Median janky frames across runs: 31. Run 2 detail: Janky frames 30 (66.67%), legacy 38
  (84.44%), 50th 150ms, 90th 350ms, 95th 400ms, 99th 400ms, Missed Vsync 21, High input
  latency 27, Slow UI thread 30, Slow bitmap uploads 0, Slow issue draw commands 15,
  Frame deadline missed 30. Across runs: Missed Vsync 21-25, High input latency 14-72,
  Slow UI thread 29-36, Slow bitmap uploads 0, Slow issue draw commands 11-15, Frame
  deadline missed == janky count.
- **GPU summary caveat:** run 2's GPU percentiles read 8ms (50th) and 4950ms
  (90th/95th/99th) with `GPU HISTOGRAM: ... 4950ms=8`. Treat the 4950ms bucket as a
  sentinel/invalid bucket, not real GPU cost.
- **Parser caveat:** framestats row parsing returned 0 rows while the summary counters
  parsed fine, so there is NO per-frame stage breakdown yet. Do not claim one.
- **Artifacts on disk:** `reports/jank/run-{1..5}-framestats.txt`,
  `reports/jank/run-{1..5}-summary.txt`, `reports/jank/trace.pb` (5,380,596 bytes).
- **Trace inspected only by string-pool grep:** the Python `perfetto` module is not
  installed and no `trace_processor`/`trace_processor_shell` is on PATH. Frequent
  slices observed: `E|684` 15885, `E|11391` 14660, `B|11391|query` 1661,
  `B|684|generateTokenForPredictions` 1400, `B|11391|TextureOp` 988,
  `B|11391|FillRectOp` 844, `B|11391|ScopedCodeCacheWrite` 836,
  `B|684|renderRate 60.00 Hz` 818, `B|684|nextAnticipatedVSyncTimeFrom` 806,
  `B|11391|FillRRectOp` 414, `B|11391|Compiling baseline` 410, `B|11391|CircleOp` 326.
  Pid attribution for 11391 and 684 is UNRESOLVED — one looks app-side, one looks
  surfaceflinger/system-side, not confirmed.
- **Negative source searches** (recorded so nobody re-runs them):
  `generateTokenForPredictions`, Integrity and Fido callers are absent from repo source;
  a Wear-source HealthConnect REST call-site grep returned 0 hits. Whole-repo
  HealthConnect files are:
  `app/src/androidTest/kotlin/tech/mmarca/openwatch/healthconnect/ClientRecordVersionTest.kt`,
  `core/health/src/main/kotlin/tech/mmarca/openwatch/healthconnect/{HealthConnectAvailabilityService.kt,
  HealthConnectGateway.kt, SyncRecordsReader.kt}`,
  `core/health/src/test/kotlin/tech/mmarca/openwatch/healthconnect/{ActivityRecordWriterTest.kt,
  InsertInChunksTest.kt}`. Wear also carries a debug-only
  `debugImplementation(libs.health.services.client)`.
- **The observed jank is NOT yet attributed.** Do not blame Compose,
  `TransformingLazyColumn`/`SurfaceTransformation`, ActivityRings, MetricTile, the
  `WearUiState` combine, the step recorder, or Health Services without frame attribution
  from the trace.
- **Unmeasured:** scripted swipe exercises only the touch scroll/draw pipeline.
  Crown/rotary latency and detent behaviour are still unmeasured; no crown baseline
  exists.
- **Harness gap:** `grep -c -iE 'keyevent|screen_off_timeout|wakefulness'
  scripts/wear-jank-capture.sh` returned 0 — the script does not wake the watch or
  extend screen-off timeout, so every automated run needs external wake/timeout until
  fixed.
- **Debug-only cost suspects** (active in `debug`, and in `ci` because `ci` is
  `initWith(debug)` and stays debuggable, so `ci` is not a debug/release discriminator):
  DebugProtocol JSONL I/O, Compose debug tooling / recomposition tracking, debug JIT
  warmup, no release log stripping. `debugImplementation(libs.health.services.client)`
  + `HealthProbeActivity` is the least plausible since the probe is opt-in via `--probe`.
- **Code/perf hot-path candidates from the earlier audit (candidates only, no
  attribution):** `WearAppViewModel.kt` builds one large `WearUiState` via
  `combine(readings.metrics(...), readings.entries(), sensors, chosen tiles+quickLog
  over Dispatchers.IO, settings)` then `.stateIn(..., WhileSubscribed(StopTimeoutMillis),
  WearUiState())`; `WearReadingsSource.kt` constants DAYS = 7,
  REFRESH_MILLIS = 30_000L, MIN_REFRESH_MILLIS = 2_000L,
  ROW_LIMIT = TileDay.MAX_HEART_RATE_SAMPLES_PER_DAY, ENTRY_LIMIT = 10_000; the
  dashboard uses `rememberTransformingLazyColumnState()` / `rememberTransformationSpec()`
  with `transformedHeight(...)` + `SurfaceTransformation(...)` applied to ListHeader and
  every MetricTile; `ActivityRings.kt` renders up to three nested
  `CircularProgressIndicator` rings with overflow; `MainActivity.kt` onResume/onPause
  call `graph.setInteractive(true/false)` which posts a host `replan()`, and
  `RecorderPlan.kt` has `stepsLive = steps && inputs.interactive && !active` with
  `StepRecorder.kt` using sensor latency 0 when live and MAX_REPORT_LATENCY_MICROS
  otherwise — i.e. entering/leaving the dashboard changes recorder behaviour.
- **Toolchain:** `gradle/gradle-daemon-jvm.properties` pins `toolchainVersion=17`; the
  Gradle launcher JVM has been observed as Java 25.0.5 while the daemon resolves 17 —
  the launcher mismatch is cosmetic, but benchmark/trace comparisons must preserve CI's
  JDK-17 discipline and echo the resolved version as evidence.
- **Invariant to preserve:** the watch merged debug manifest must NOT contain
  `INTERNET`, `ACCESS_NETWORK_STATE`, or `ACCESS_WIFI_STATE`; the manifest grep gate
  must stay green for any future change.
- **No committed code change** came out of this session; the checkout is clean at
  `bcc6663` before this docs commit. **Process lesson:** two earlier agent attempts
  timed out with zero artifacts because of oversized scope plus Gradle lock contention —
  future agent work must be scoped smaller, commit-per-deliverable, and serialize
  Gradle with `./gradlew --stop` before benchmark/build runs.

### 2026-11-26 (evening) — Claude Code (session: M0 + day-of-wear report)
- Paired the phone over Wireless debugging (Pixel 10 Pro, API 37), `./gradlew :app:assembleDebug`
  and installed `tech.mmarca.openwatch.debug`; Matt granted Health Connect, added the watch,
  allowed the association and validated. Association id 5 on the phone.
- Verified sync without screens first: the phone app is debuggable, so
  `adb shell run-as tech.mmarca.openwatch.debug cat databases/openwatch.db(-wal)` + Python sqlite
  shows `device_state` cursors (`hr_cursor`, `steps_cursor`, `ascent_cursor`, `sleep_cursor`;
  `hv`/`rr`/`o2` stay `t0` as expected). `dumpsys healthconnect` shows OpenWatch "Contributed
  Data: true" but no per-type counts; the Health Connect UI (Data and access → Steps / Heart
  rate) showed OpenWatch entries.
- Live manual sync log (`logcat` tag `OpenWatchWearOs`): 100 HR samples in 19 min (10 s
  cadence), steps 4 minutes, ascent 0 floors (3.1 m without steps), sleep 0 nights (daytime),
  hrv/breathing/spo2 0, then zones/profile/days sent to the watch.
- Day-of-wear report pulled to the scratchpad (personal data, not committed); findings in the
  research doc's "a day of wear" section; Q3 and Q4 answered.
- Matt confirmed on the wrist: the heart rate and steps complications work on the stock face,
  and the tiles appear in the tile list (Q7). M0 complete.
- Tip: set the phone not to lock (Matt did) or every screenshot/uiautomator dump comes back black.

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
