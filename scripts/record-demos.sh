#!/usr/bin/env bash
# Record the three render-effectx demo slides on a connected device and pull the clips.
#
# Each slide is recorded on its own so the tiles have no swipe transitions in them:
#   slide 0 (Signal) — auto, no interaction
#   slide 1 (Glitch) — taps the GLITCH button to fire bursts
#   slide 2 (Chroma) — taps the photo to send ripples
#
# Output: $OUT/rec_signal.mp4, rec_glitch.mp4, rec_chroma.mp4 (landscape, device resolution).
# Coordinates are tuned for a 2340x1080 landscape display (Xiaomi Mi 9). Adjust if your device
# differs. Requires: adb, a landscape-locked DemoPagerActivity already installed and launched.
set -euo pipefail

PKG=dev.serhiiyaremych.rendereffectx
ACT=$PKG/.DemoPagerActivity
OUT=${1:-$(pwd)/demo/raw}
SECS=7
BR=16000000
mkdir -p "$OUT"

launch() { adb shell am force-stop "$PKG"; adb shell am start -n "$ACT" >/dev/null; sleep 4; }
swipe_next() { adb shell input swipe 1900 540 400 540 250; sleep 1.2; }
rec() { adb shell screenrecord --time-limit "$SECS" --bit-rate "$BR" "/sdcard/$1" & }
pull() { wait; adb pull "/sdcard/$1" "$OUT/$1"; }

# Slide 0 — Signal (auto rolling interference band)
launch
rec rec_signal.mp4
pull rec_signal.mp4

# Slide 1 — Glitch (fire bursts while it idles)
swipe_next
rec rec_glitch.mp4
sleep 0.6; adb shell input tap 300 820
sleep 2.2; adb shell input tap 300 820
sleep 2.2; adb shell input tap 300 820
pull rec_glitch.mp4

# Slide 2 — Chroma (ripples from several tap points)
swipe_next
rec rec_chroma.mp4
sleep 0.5; adb shell input tap 1500 400
sleep 2.0; adb shell input tap 1950 760
sleep 2.0; adb shell input tap 1680 560
pull rec_chroma.mp4

echo "done → $OUT"
