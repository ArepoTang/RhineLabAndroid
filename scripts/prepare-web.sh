#!/bin/sh
# Copies a built RhineLabUI web release into the APK assets and derives the
# launcher icon from it. Run once before any Gradle build.
#
#   ./scripts/prepare-web.sh [path-to/dist]
#
# Default source is a sibling checkout at ../RhineLabUI/dist.
#
# Note: the web build references fonts and assets with root-absolute URLs
# (/fonts/..., /assets/...). That is why the files go to the assets *root* and
# MainActivity maps the "/" prefix with WebViewAssetLoader, instead of nesting
# them under assets/www/ and rewriting every URL.
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:-$ROOT/../RhineLabUI/dist}"
DST="$ROOT/app/src/main/assets"

if [ ! -f "$SRC/index.html" ]; then
  echo "prepare-web: $SRC/index.html not found." >&2
  echo "Build it first:  cd ../RhineLabUI && npm ci && npm run build" >&2
  exit 1
fi

# Flatten: everything the site serves sits at the assets root.
find "$DST" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
cp -R "$SRC/." "$DST/"

# A service worker inside the APK would keep serving a stale build after an app
# update, and there is nothing to update from: the site ships in the APK.
rm -f "$DST/sw.js" "$DST/update.html" "$DST/update.js" "$DST/manifest.webmanifest" \
      "$DST/preview.gif" "$DST/preview.jpg"

# Launcher icon, taken from the web app's own icon set so the two stay in sync.
ICON="$DST/icons/icon-512.png"
if [ -f "$ICON" ]; then
  mkdir -p "$ROOT/app/src/main/res/mipmap-xxxhdpi"
  cp "$ICON" "$ROOT/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
fi

FILES=$(find "$DST" -type f | wc -l | tr -d ' ')
SIZE=$(du -sh "$DST" | cut -f1)
echo "prepare-web: $FILES files, $SIZE -> app/src/main/assets"
