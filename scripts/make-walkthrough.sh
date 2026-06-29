#!/usr/bin/env bash
# Turn the raw full walkthrough recording (demo/raw/walk_raw.mp4 — one continuous scroll through
# the three demo pages) into the README assets:
#   demo/walkthrough.mp4    download-quality clip
#   demo/walkthrough.webp   looping, inline-on-GitHub animation
#
# walk_raw is a 2340x1080 landscape screen capture. Both outputs scale it 2/3 → 1560x720 and clone
# the last (settled) frame for HOLD seconds so the final chromatic ripple finishes and rests before
# the loop restarts — without the hold it snaps back the instant the ripple lands and reads as cut.
#
# NOTE: img2webp defaults to *lossless* per frame (every frame a full keyframe → ~10x bigger). The
# -lossy flag is what makes this ~800KB instead of ~9MB; keep it. Requires: ffmpeg + img2webp.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RAW="$ROOT/demo/raw/walk_raw.mp4"
OUT="$ROOT/demo"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

HOLD=1.8        # seconds to hold the final settled frame before the loop
SCALE=1560:720  # 2/3 of the 2340x1080 capture
WEBP_W=720      # webp canvas width (height auto)
WEBP_FPS=16     # sampled content fps; played at 42ms/frame ≈ snappy 1.5x pace
WEBP_Q=70

# mp4 — scale + freeze tail
ffmpeg -y -i "$RAW" -vf "scale=$SCALE,tpad=stop_mode=clone:stop_duration=$HOLD" \
  -c:v libx264 -crf 26 -preset slow -pix_fmt yuv420p -movflags +faststart \
  "$OUT/walkthrough.mp4" -loglevel error
echo "walkthrough.mp4  $(du -h "$OUT/walkthrough.mp4" | cut -f1)"

# webp — sample the padded mp4 to frames, encode lossy/looping
ffmpeg -y -i "$OUT/walkthrough.mp4" -vf "fps=$WEBP_FPS,scale=$WEBP_W:-2" "$TMP/w_%04d.png" -loglevel error
img2webp -lossy -loop 0 -d 42 -q "$WEBP_Q" -m 6 "$TMP"/w_*.png -o "$OUT/walkthrough.webp" >/dev/null
echo "walkthrough.webp  $(du -h "$OUT/walkthrough.webp" | cut -f1)"

echo "done → $OUT"
