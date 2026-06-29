#!/usr/bin/env bash
# Record the standalone invert scene (InvertDemoActivity — Compose logo + slider) on a connected
# device and turn it into the README asset demo/invert.webp.
#
# The scene's slider auto-sweeps amount 0->1->0 with a fixed 3.6s period (tween(1800) reversed), and
# the animation is wall-clock based, so cutting *exactly one period* loops seamlessly no matter when
# capture starts; EaseInOutSine's zero velocity at the extremes hides the seam. -lossy is required
# (img2webp defaults to lossless → ~10x bigger). Requires: adb, ffmpeg, img2webp.
#
# Crop is tuned for a 1080x2340 portrait device (Xiaomi Mi 9). Adjust CROP if yours differs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/demo"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

PKG=dev.serhiiyaremych.rendereffectx
ACT=$PKG/.InvertDemoActivity
PERIOD=3.6                       # one full 0->1->0 sweep
CROP="crop=720:1050:180:650"     # card + label + slider, centred
SCALE=480                        # output width (height auto)
FPS=25

adb shell am force-stop "$PKG"
adb shell am start -n "$ACT" >/dev/null
sleep 2.5                        # warm up; phase is irrelevant (see header)
adb shell screenrecord --time-limit 8 --bit-rate 20000000 /sdcard/invert_rec.mp4
adb pull /sdcard/invert_rec.mp4 "$TMP/rec.mp4" >/dev/null

ffmpeg -y -ss 2.0 -t "$PERIOD" -i "$TMP/rec.mp4" \
  -vf "$CROP,scale=$SCALE:-2,fps=$FPS" "$TMP/i_%03d.png" -loglevel error
img2webp -lossy -loop 0 -d 40 -q 75 -m 6 "$TMP"/i_*.png -o "$OUT/invert.webp" >/dev/null
echo "invert.webp  $(du -h "$OUT/invert.webp" | cut -f1)"

# full-fps, native-resolution mp4 of the same one-period cut — visually-lossless companion
ffmpeg -y -ss 2.0 -t "$PERIOD" -i "$TMP/rec.mp4" -vf "$CROP" \
  -c:v libx264 -crf 16 -preset slow -pix_fmt yuv420p -movflags +faststart \
  "$OUT/invert.mp4" -loglevel error
echo "invert.mp4   $(du -h "$OUT/invert.mp4" | cut -f1)"
