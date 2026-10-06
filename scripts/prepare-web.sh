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
# chosen here instead of by Wallpaper Engine. See the python patch below.
python3 - "$DST/index.html" <<'PY'
import sys
path = sys.argv[1]
html = open(path, encoding="utf-8").read()
# Last classic script before the deferred module: the host shim above has already
# created rhineWallpaperHost / wallpaperPropertyListener, the module has not read
# them yet. host-settings.js layers the terminal's own defaults and settings panel
# on top. See that file for why this exists at all.
anchor = '<script type="module"'
tag = '<script src="./host-settings.js"></script>\n    '
if tag not in html:
    if anchor not in html:
        sys.exit("prepare-web: no module script tag in index.html; upstream changed it")
    open(path, "w", encoding="utf-8").write(html.replace(anchor, tag + anchor, 1))
    print("prepare-web: injected host-settings.js")
PY

# Launcher icon, taken from the web app's own icon set so the two stay in sync.
ICON="$DST/icons/icon-512.png"
if [ -f "$ICON" ]; then
  mkdir -p "$ROOT/app/src/main/res/mipmap-xxxhdpi"
  cp "$ICON" "$ROOT/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
fi

cp "$ROOT/scripts/host-settings.js" "$DST/host-settings.js"

FILES=$(find "$DST" -type f | wc -l | tr -d ' ')
SIZE=$(du -sh "$DST" | cut -f1)
echo "prepare-web: $FILES files, $SIZE -> app/src/main/assets/www"
