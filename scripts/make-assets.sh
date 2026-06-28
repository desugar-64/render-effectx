#!/usr/bin/env bash
# Turn the raw demo recordings (demo/raw/*.mp4) into README assets:
#   demo/tile_signal.webp  demo/tile_glitch.webp  demo/tile_chroma.webp   (looping per-effect tiles)
#   demo/still_*.png                                                       (static frames)
#   demo/banner.webp                                                       (fused triptych, all 3)
#
# Animated WebP (looping, no player chrome, full colour) is the chosen format. Requires:
# ffmpeg + img2webp (libwebp). The card crop is tuned for a 2340x1080 landscape capture.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RAW="$ROOT/demo/raw"
OUT="$ROOT/demo"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

CROP="crop=900:900:1190:110"   # the player/profile/photo card on the right of the slide
TILE=340                       # tile px (square)
FPS=15
DELAY=66                       # ms/frame ≈ 15fps for img2webp
DUR=4.5                        # loop length (s) — these effects are noisy, so keep loops short
TILE_Q=62
BANNER_TILE=300
BANNER_Q=52

# A looping tile from one recording: crop → square → frames → animated webp.
# Optional per-effect overrides: tile <name> <ss> [quality] [fps] — the glitch frames are nearly
# incompressible (full-frame chromatic noise every frame), so it gets a lower fps/quality.
tile() {
  local name="$1" ss="$2" q="${3:-$TILE_Q}" fps="${4:-$FPS}" delay
  delay=$(( 1000 / fps ))
  rm -rf "$TMP/$name"; mkdir -p "$TMP/$name"
  ffmpeg -y -ss "$ss" -t "$DUR" -i "$RAW/rec_$name.mp4" \
    -vf "$CROP,scale=$TILE:$TILE,fps=$fps" "$TMP/$name/f_%03d.png" -loglevel error
  img2webp -loop 0 -d "$delay" -q "$q" -m 6 "$TMP/$name"/f_*.png -o "$OUT/tile_$name.webp" >/dev/null
  echo "tile_$name.webp  $(du -h "$OUT/tile_$name.webp" | cut -f1)"
}

# A representative still (cropped card) at a chosen timestamp.
still() {
  ffmpeg -y -ss "$2" -i "$RAW/rec_$1.mp4" -vf "$CROP,scale=$TILE:$TILE" -frames:v 1 \
    "$OUT/still_$1.png" -loglevel error
}

tile signal 0.3
tile glitch 0.4 42 10
tile chroma 0.3

still signal 2.7
still glitch 6.6
still chroma 3.4

# Fused triptych banner: the three cropped cards side by side, each looping in its own panel.
rm -rf "$TMP/banner"; mkdir -p "$TMP/banner"
ffmpeg -y \
  -ss 0.3 -t 3.6 -i "$RAW/rec_signal.mp4" \
  -ss 0.4 -t 3.6 -i "$RAW/rec_glitch.mp4" \
  -ss 0.3 -t 3.6 -i "$RAW/rec_chroma.mp4" \
  -filter_complex "
    [0:v]$CROP,scale=$BANNER_TILE:$BANNER_TILE,fps=10[a];
    [1:v]$CROP,scale=$BANNER_TILE:$BANNER_TILE,fps=10[b];
    [2:v]$CROP,scale=$BANNER_TILE:$BANNER_TILE,fps=10[c];
    [a][b][c]hstack=inputs=3[v]" -map "[v]" "$TMP/banner/f_%03d.png" -loglevel error
img2webp -loop 0 -d 100 -q "$BANNER_Q" -m 6 "$TMP/banner"/f_*.png -o "$OUT/banner.webp" >/dev/null
echo "banner.webp  $(du -h "$OUT/banner.webp" | cut -f1)"

echo "done → $OUT"
