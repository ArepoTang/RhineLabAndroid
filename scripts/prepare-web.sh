#!/bin/sh
# Copies a built RhineLabWallpaper release into the APK assets and derives the
# launcher icon from it. Run once before any Gradle build.
#
#   ./scripts/prepare-web.sh [path-to/release/wallpaper]
#
# Default source is a sibling checkout at ../RhineLabWallpaper-src.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:-$ROOT/../RhineLabWallpaper-src/release/wallpaper}"
DST="$ROOT/app/src/main/assets/www"

if [ ! -f "$SRC/index.html" ]; then
  echo "prepare-web: $SRC/index.html not found." >&2
  echo "Build it first:  cd ../RhineLabWallpaper-src && npm ci && npm run build:wallpaper" >&2
  exit 1
fi

rm -rf "$DST"
mkdir -p "$DST"
cp -R "$SRC/." "$DST/"

# Store-facing files the terminal never reads, plus the local probe page.
rm -f "$DST/project.json" "$DST/build-files.json" "$DST/preview.gif" "$DST/preview.jpg" "$DST/__probe.html"

# The APK is its own wallpaper host, so the frame budget and the quality tier are
# chosen here instead of by Wallpaper Engine. Upstream ships `fps: 30` with no
# `renderquality`, which is the full "original" preset (pixelRatio 1.5 + AO + DoF)
# -- too much for a phone that is fill-rate bound. Fails loudly if upstream moves
# the shim, because a silent no-op would ship the slow defaults unnoticed.
python3 - "$DST/index.html" <<'PY'
import sys
path = sys.argv[1]
html = open(path, encoding="utf-8").read()
old = 'window.rhineWallpaperHost = { properties: {}, fps: 30, paused: false };'
new = ('window.rhineWallpaperHost = { properties: { renderquality: { value: "performance" } },'
       ' fps: 60, paused: false };')
if old not in html:
    if new in html:
        sys.exit(0)
    sys.exit("prepare-web: host shim not found in index.html; upstream changed it")
open(path, "w", encoding="utf-8").write(html.replace(old, new, 1))
print("prepare-web: host defaults -> 60 fps, performance quality")
PY

# Launcher icon, taken from the web app's own icon set so the two stay in sync.
ICON="$DST/icons/icon-512.png"
if [ -f "$ICON" ]; then
  mkdir -p "$ROOT/app/src/main/res/mipmap-xxxhdpi"
  cp "$ICON" "$ROOT/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
fi

FILES=$(find "$DST" -type f | wc -l | tr -d ' ')
SIZE=$(du -sh "$DST" | cut -f1)
echo "prepare-web: $FILES files, $SIZE -> app/src/main/assets/www"
