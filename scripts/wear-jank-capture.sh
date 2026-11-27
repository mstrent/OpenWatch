#!/usr/bin/env sh
# Capture a jank baseline for the OpenWatch watch app's dashboard over adb.
#
#   scripts/wear-jank-capture.sh [-s <serial>] [-p <pkg>] [-r <runs>] [-o <outdir>]
#
#   -s <serial>  adb serial to measure (default: the single attached device)
#   -p <pkg>     package to measure (default: tech.mmarca.openwatch.debug)
#   -r <runs>    force-stop/relaunch/scroll runs (default: 5)
#   -o <outdir>  output directory (default: reports/jank/, gitignored)
#
# Per run: force-stop, relaunch, `dumpsys gfxinfo reset`, the same fixed
# swipes down the dashboard, then dump `framestats` and the summary block.
# Then one 10 s Perfetto trace with the same swipes running under it. Ends
# with a per-run table (janky frames, % janky, p90/p95/p99) and the MEDIAN
# janky-frame count across runs — medians, never means, because one cold
# first run would drag a mean up.
#
# Two fidelity warnings that change how the numbers read:
#   - `input swipe` drives the touch scroll/draw pipeline. The rotary
#     encoder (the crown) is a separate input path; crown latency needs a
#     real watch. A swipe baseline is NOT a crown baseline.
#   - On an emulator these counts are only comparable A/B against the same
#     AVD, host state, and build variant — never as milliseconds.
set -eu

PROG="${0##*/}"
PKG="tech.mmarca.openwatch.debug"
RUNS=5
OUTDIR="reports/jank"
SERIAL="${ANDROID_SERIAL:-}"
ACTIVITY="tech.mmarca.openwatch.wear.MainActivity"
SETTLE=3  # seconds after launch before the counters are reset

say()  { printf '\033[1m%s\033[0m\n' "$*"; }
note() { printf '  %s\n' "$*"; }
warn() { printf '\033[33mwarning:\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

usage() { sed -n '2,23p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }

# adb, self-contained (this script must not depend on scripts/lib/).
find_adb() {
    if command -v adb >/dev/null 2>&1; then
        echo adb
        return
    fi
    adb_candidate=""
    for adb_candidate in \
        "${ANDROID_HOME:-}/platform-tools/adb" \
        "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
        "$HOME/Android/Sdk/platform-tools/adb"; do
        if [ -x "$adb_candidate" ]; then
            echo "$adb_candidate"
            return
        fi
    done
    die "adb not found. Install Android platform-tools, or set ANDROID_HOME to the SDK."
}

while [ $# -gt 0 ]; do
    case "$1" in
        -s) SERIAL="$2"; shift 2 ;;
        -p) PKG="$2"; shift 2 ;;
        -r) RUNS="$2"; shift 2 ;;
        -o) OUTDIR="$2"; shift 2 ;;
        -h|--help) usage ;;
        *) die "unknown argument $1 (try -h)" ;;
    esac
done
case "$RUNS" in ''|*[!0-9]*) die "-r wants a whole number of runs, got '$RUNS'" ;; esac
[ "$RUNS" -ge 1 ] || die "-r must be at least 1"

ADB="$(find_adb)"
adbw() {
    if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi
}
# adb's shell output carries \r; strip it so awk sees clean lines.
shell() { adbw shell "$@" | tr -d '\r'; }

# A device must be there and booted. With nothing attached today this MUST
# fail loudly — that clean failure is the expected behaviour, not a bug.
if [ -n "$SERIAL" ]; then
    state="$(adbw get-state 2>/dev/null || true)"
    [ "$state" = device ] || die "no device at serial '$SERIAL' (state: ${state:-none}). Attach a watch (scripts/wear-pair.sh) or an emulator, then rerun."
else
    device_count="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }' | awk 'END { print NR + 0 }')"
    [ "$device_count" -gt 0 ] || die "adb sees no device ('adb devices' is empty). Attach a watch over wireless debugging (scripts/wear-pair.sh) or start an emulator, then rerun. Nothing was measured."
    [ "$device_count" -le 1 ] || die "adb sees $device_count devices; pick the watch with -s <serial>."
    SERIAL="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
fi
boot="$(shell getprop sys.boot_completed)"
[ "$boot" = 1 ] || die "device '$SERIAL' has not finished booting (sys.boot_completed='${boot:-empty}'). Wait for it to boot, then rerun."
note "measuring $PKG on $SERIAL (Android $(shell getprop ro.build.version.release), API $(shell getprop ro.build.version.sdk))"

# The app must be installed before any of this means anything.
if ! shell pm list packages "$PKG" | grep -qx "package:$PKG"; then
    die "$PKG is not installed on $SERIAL. Install it first (scripts/wear-install.sh)."
fi

warn "input swipe exercises the touch scroll/draw pipeline, NOT the rotary encoder — crown latency needs a real watch."
warn "on an emulator, treat these counts as relative A/B on an identical AVD/host/variant only, never as milliseconds."

# Fixed geometry: the same two swipes down the list and back, same distance,
# every run — jank deltas between runs must come from the app, not the gestures.
size="$(shell wm size | awk '/Physical size/ { print $3 }')"
case "$size" in
    [0-9]*x[0-9]*) ;;
    *) size="390x390"; warn "could not read the screen size; assuming $size" ;;
esac
width="${size%x*}"
height="${size#*x}"
swipe_x=$((width / 2))
swipe_from=$((height * 80 / 100))
swipe_to=$((height * 20 / 100))

scroll_dashboard() {
    swipe_i=0
    while [ "$swipe_i" -lt 4 ]; do
        shell input swipe "$swipe_x" "$swipe_from" "$swipe_x" "$swipe_to" 300
        swipe_i=$((swipe_i + 1))
        sleep 0.6
    done
    swipe_i=0
    while [ "$swipe_i" -lt 2 ]; do
        shell input swipe "$swipe_x" "$swipe_to" "$swipe_x" "$swipe_from" 300
        swipe_i=$((swipe_i + 1))
        sleep 0.6
    done
}

mkdir -p "$OUTDIR"
ROWS="$OUTDIR/.runs.$$.tsv"
JANKS="$OUTDIR/.janks.$$.txt"
trap 'rm -f "$ROWS" "$JANKS"' EXIT INT TERM

# Pull one stat field out of the gfxinfo summary block. Field positions are
# stable across recent Android releases ("Total frames rendered: N",
# "Janky frames: N (P%)", "90th percentile: Nms"); the %-form fallbacks cover
# older dumpsys.
run_stats() {
    summary="$1"
    TOTAL="$(awk '/Total frames rendered:/ { print $4; exit }' "$summary")"
    JANKY="$(awk '/Janky frames:/ { print $3; exit }' "$summary")"
    JPCT="$(awk '/Janky frames:/ { gsub(/[()%]/, ""); print $4; exit }' "$summary")"
    P90="$(awk '/90th percentile:/ { gsub(/ms/, ""); print $3; exit }' "$summary")"
    P95="$(awk '/95th percentile:/ { gsub(/ms/, ""); print $3; exit }' "$summary")"
    P99="$(awk '/99th percentile:/ { gsub(/ms/, ""); print $3; exit }' "$summary")"
    [ -n "$P90" ] || P90="$(awk '/90%/ { gsub(/ms/, ""); print $NF; exit }' "$summary")"
    [ -n "$P95" ] || P95="$(awk '/95%/ { gsub(/ms/, ""); print $NF; exit }' "$summary")"
    [ -n "$P99" ] || P99="$(awk '/99%/ { gsub(/ms/, ""); print $NF; exit }' "$summary")"
}

capture_run() {
    run="$1"
    say "run $run/$RUNS: relaunch, reset, scroll, dump"
    shell am force-stop "$PKG"
    shell am start -W -n "$PKG/$ACTIVITY" >/dev/null
    sleep "$SETTLE"
    shell dumpsys gfxinfo "$PKG" reset >/dev/null
    sleep 1
    scroll_dashboard
    shell dumpsys gfxinfo "$PKG" framestats > "$OUTDIR/run-$run-framestats.txt"
    shell dumpsys gfxinfo "$PKG" > "$OUTDIR/run-$run-summary.txt"
    run_stats "$OUTDIR/run-$run-summary.txt"
    if [ -z "$TOTAL" ] || [ "$TOTAL" = 0 ]; then
        warn "run $run rendered no measured frames (the app may have drawn nothing yet) — recorded as 0 janky"
        TOTAL=0; JANKY=0; JPCT=n/a; P90=n/a; P95=n/a; P99=n/a
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$run" "$TOTAL" "$JANKY" "$JPCT" "$P90" "$P95" "$P99" >> "$ROWS"
    printf '%s\n' "$JANKY" >> "$JANKS"
    note "frames $TOTAL, janky $JANKY ($JPCT%), p90 ${P90}ms p95 ${P95}ms p99 ${P99}ms"
}

# One Perfetto trace with scrolling under it. Runs device-side for 10 s; the
# swipes on the host side ride inside that window. Failure is a warning, not
# a death: tracing is blocked on some production builds.
capture_trace() {
    say "Perfetto trace (10 s) with the same swipes under it"
    trace_dev=/data/misc/perfetto-traces/openwatch-jank.pb
    adbw shell "perfetto -o $trace_dev -t 10s gfx sched freq sched_boost pm input wm composereport view scheduler am dalvik" &
    trace_pid=$!
    sleep 1
    scroll_dashboard
    scroll_dashboard
    if ! wait "$trace_pid" 2>/dev/null; then
        warn "device-side perfetto failed (older device, or tracing blocked); continuing without a trace"
        return 0
    fi
    sleep 1
    if adbw pull "$trace_dev" "$OUTDIR/trace.pb" >/dev/null 2>&1; then
        note "saved $OUTDIR/trace.pb — open it at https://ui.perfetto.dev"
    else
        warn "perfetto ran but the pull failed; check /data/misc/perfetto-traces on the device"
    fi
    shell rm "$trace_dev" 2>/dev/null || true
}

run=1
while [ "$run" -le "$RUNS" ]; do
    capture_run "$run"
    run=$((run + 1))
done
capture_trace

median() {
    sort -n | awk '
        { v[NR] = $1 }
        END {
            if (NR == 0) { print "n/a"; exit }
            if (NR % 2) printf "%s\n", v[(NR + 1) / 2]
            else printf "%.1f\n", (v[NR / 2] + v[NR / 2 + 1]) / 2
        }'
}

say "Summary — $PKG on $SERIAL"
printf '%-5s %8s %7s %8s %7s %7s %7s\n' run frames janky "janky%" "p90ms" "p95ms" "p99ms"
awk -F '\t' '{ printf "%-5s %8s %7s %8s %7s %7s %7s\n", $1, $2, $3, $4, $5, $6, $7 }' "$ROWS"
note "median janky frames across the $RUNS runs: $(median < "$JANKS")"
note "per-run detail: $OUTDIR/run-N-framestats.txt, run-N-summary.txt, trace.pb"
