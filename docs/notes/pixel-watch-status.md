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

## Status (as of 2026-11-26)

- **Phase: documentation + survey planning. No code changed yet on this branch.**
- Durable fork and clone set up; docs branch pushed (`f3b2fb5`).
- Feasibility verdict: **plausible without code changes** — `WatchSensors`
  capability gates already degrade metrics off on a watch that lacks a sensor
  route; HRV/respiratory rate/SpO2/skin temp degrade off until an official route
  is found or the survey proves a raw one.
- The gating move now is the **on-device debug survey** of a real Pixel Watch 5
  (steps/HR presence, `TYPE_HEART_BEAT`, hub batching, HR contention with the
  stock stack, Health Services capability list with Fitbit signed out).
- Metric-coverage code changes are **held until the survey results land**, and
  need Matt's approval before implementation.

## Decisions log

- **2026-11-26 — Fork is the working copy.** All Pixel work happens on
  `mstrent/OpenWatch` branch `pixel-watch-support`; PR back upstream only after
  the effort is done (a ways off). Ephemeral sandbox checkouts are not trusted for
  work: the 2026-11-25 working tree at `/tmp/OpenWatch` was lost with its
  uncommitted docs.
- **2026-11-26 — Google proprietary APIs/libraries are allowed** on the fork when
  they unlock sensors or data (supersedes the earlier "stay off Health Services"
  instinct for our purposes). Upstream's raw-sensors-only rule still stands for
  anything we want merged; the fork exists so the dependency question can be
  explored without holding the family's watches hostage.
- **Workspace accounts are verified working** for the family's Pixel Watch setup
  (with the domain's Device Management off). Not a blocker; do not relitigate
  personal-vs-Workspace advice.
- Sideloading / onboarding bypass is **historical**: not needed for the family's
  already set-up, paired watches. `scripts/wear-install.sh` over wireless debug
  is the whole install procedure for us.
- No metric-coverage code change before the Pixel survey (gates already handle it).

## What is verified vs. assumed

- **Verified by this project**: everything marked verified in
  `docs/research/wear-sensor-map.md` and `docs/research/wear-ppg-hrv.md`
  (TicWatch Pro 5, Galaxy Watch8 captures; tiles/complications on TicWatch).
- **Verified by Matt (household)**: Workspace-account setup works on the family's
  Pixel Watches.
- **Community-reported, not verified here**: Pixel sensor list details,
  pixelbridge/Gadgetbridge routes, pre-onboarding adb sideload on Pixel Watch.
- **Untested hypotheses** (survey questions 1–8 in the research doc): Pixel hub
  batching vs `hub-40s`, HR arbitration with the stock Google stack, stock-face
  complication slots accepting OpenWatch providers, Wear OS 7 tiles vs new Wear
  Widgets, bedtime/DND effect on night accelerometer, `TYPE_HEART_BEAT` presence,
  Health Services signed-out capability shrinkage.

## Next actions

1. **Survey a real Pixel Watch 5** (Matt's watches, already paired):
   - enable wireless debugging, pair, `scripts/wear-install.sh --serial <ip>:<port>`
   - `python3 -I scripts/wear-debug-report.py --serial <ip>:<port>`
   - `adb -s <watch> shell dumpsys sensorservice | sed -n '/Sensor List/,/^$/p'`
   - answer survey questions 1–8; write findings back into the research doc.
2. Decide the **Health Services dependency split** (fork keeps it if needed;
   upstream PR would present the Jetpack-library-vs-closed-`.aar` argument).
3. Only after 1–2: implement code (P2 batching fallback / P3 HR duty-cycle
   hardening are conditional on survey results). **Ask Matt before code.**
4. Verify with `./gradlew verifyCi` + `verifyWearCi` and
   `python3 scripts/sync-wiki.py --repo mstrent/OpenWatch --check` before any push.

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
