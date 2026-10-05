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

# Launcher icon, taken from the web app's own icon set so the two stay in sync.
ICON="$DST/icons/icon-512.png"
if [ -f "$ICON" ]; then
  mkdir -p "$ROOT/app/src/main/res/mipmap-xxxhdpi"
  cp "$ICON" "$ROOT/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
fi

FILES=$(find "$DST" -type f | wc -l | tr -d ' ')
SIZE=$(du -sh "$DST" | cut -f1)
echo "prepare-web: $FILES files, $SIZE -> app/src/main/assets/www"
