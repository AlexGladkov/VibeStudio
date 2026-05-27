#!/usr/bin/env bash
# =============================================================================
# generate-icons.sh — VibeStudio Desktop Icon Generator
#
# Usage:
#   ./desktopApp/icons/generate-icons.sh
#
# Requirements (macOS):
#   - sips      (built-in on macOS, used for PNG resizing)
#   - iconutil  (built-in on macOS, used to build .icns)
#   - ImageMagick (optional, used for .ico and text rendering)
#     Install: brew install imagemagick
#
# Output files (in this directory):
#   icon.png   — 1024x1024 master PNG (also used for Linux)
#   icon.icns  — macOS bundle icon
#   icon.ico   — Windows installer icon
#
# After running, these files are referenced in desktopApp/build.gradle.kts
# via nativeDistributions { macOS/windows/linux { iconFile.set(...) } }.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ICONSET_DIR="${SCRIPT_DIR}/AppIcon.iconset"
MASTER_PNG="${SCRIPT_DIR}/icon.png"
ICNS_OUT="${SCRIPT_DIR}/icon.icns"
ICO_OUT="${SCRIPT_DIR}/icon.ico"

# -----------------------------------------------------------------------------
# Step 1 — Generate 1024×1024 master PNG
# -----------------------------------------------------------------------------

echo "==> Generating master PNG (1024x1024)..."

if command -v convert &>/dev/null; then
    # ImageMagick path — produces a nicer icon with "VS" text
    convert \
        -size 1024x1024 \
        xc:"#1E1E2E" \
        -fill "#CDD6F4" \
        -font "Helvetica-Bold" \
        -pointsize 420 \
        -gravity Center \
        -annotate 0 "VS" \
        -format PNG \
        "${MASTER_PNG}"
    echo "    Created via ImageMagick: ${MASTER_PNG}"
else
    # sips-only fallback — solid color square (no text without ImageMagick)
    # Creates a 1×1 pixel PNG and scales it up; replace with real artwork.
    echo "    WARNING: ImageMagick not found. Creating plain color placeholder."
    echo "    Install ImageMagick for text rendering: brew install imagemagick"

    # Minimal valid 1×1 blue PNG (raw bytes via Python — available on all macOS)
    python3 - <<'PYEOF'
import struct, zlib, sys

def png_chunk(name, data):
    c = zlib.crc32(name + data) & 0xffffffff
    return struct.pack(">I", len(data)) + name + data + struct.pack(">I", c)

sig = b'\x89PNG\r\n\x1a\n'
ihdr = png_chunk(b'IHDR', struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
# RGB pixel: #1E1E2E
raw = b'\x00\x1e\x1e\x2e'
idat = png_chunk(b'IDAT', zlib.compress(raw))
iend = png_chunk(b'IEND', b'')

import os
path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'icon.png')
with open(path, 'wb') as f:
    f.write(sig + ihdr + idat + iend)
print(f"    Wrote placeholder: {path}")
PYEOF
fi

# -----------------------------------------------------------------------------
# Step 2 — Generate .icns (macOS)
# -----------------------------------------------------------------------------

echo "==> Building .icns from master PNG..."

mkdir -p "${ICONSET_DIR}"

declare -a SIZES=(16 32 64 128 256 512 1024)

for size in "${SIZES[@]}"; do
    sips -z "${size}" "${size}" "${MASTER_PNG}" \
        --out "${ICONSET_DIR}/icon_${size}x${size}.png" \
        --setProperty format png \
        > /dev/null 2>&1
done

# iconutil expects exactly these filenames in the .iconset bundle
cp "${ICONSET_DIR}/icon_16x16.png"    "${ICONSET_DIR}/icon_16x16.png"
cp "${ICONSET_DIR}/icon_32x32.png"    "${ICONSET_DIR}/icon_16x16@2x.png"
cp "${ICONSET_DIR}/icon_32x32.png"    "${ICONSET_DIR}/icon_32x32.png"
cp "${ICONSET_DIR}/icon_64x64.png"    "${ICONSET_DIR}/icon_32x32@2x.png"
cp "${ICONSET_DIR}/icon_128x128.png"  "${ICONSET_DIR}/icon_128x128.png"
cp "${ICONSET_DIR}/icon_256x256.png"  "${ICONSET_DIR}/icon_128x128@2x.png"
cp "${ICONSET_DIR}/icon_256x256.png"  "${ICONSET_DIR}/icon_256x256.png"
cp "${ICONSET_DIR}/icon_512x512.png"  "${ICONSET_DIR}/icon_256x256@2x.png"
cp "${ICONSET_DIR}/icon_512x512.png"  "${ICONSET_DIR}/icon_512x512.png"
cp "${ICONSET_DIR}/icon_1024x1024.png" "${ICONSET_DIR}/icon_512x512@2x.png"

iconutil --convert icns "${ICONSET_DIR}" --output "${ICNS_OUT}"
rm -rf "${ICONSET_DIR}"

echo "    Created: ${ICNS_OUT}"

# -----------------------------------------------------------------------------
# Step 3 — Generate .ico (Windows)
# -----------------------------------------------------------------------------

echo "==> Building .ico from master PNG..."

if command -v convert &>/dev/null; then
    convert "${MASTER_PNG}" \
        \( -clone 0 -resize 256x256 \) \
        \( -clone 0 -resize 128x128 \) \
        \( -clone 0 -resize 64x64  \) \
        \( -clone 0 -resize 48x48  \) \
        \( -clone 0 -resize 32x32  \) \
        \( -clone 0 -resize 16x16  \) \
        -delete 0 \
        "${ICO_OUT}"
    echo "    Created: ${ICO_OUT}"
else
    echo "    WARNING: ImageMagick not found — skipping .ico generation."
    echo "    Install: brew install imagemagick  then re-run this script."
fi

# -----------------------------------------------------------------------------
# Done
# -----------------------------------------------------------------------------

echo ""
echo "Icon generation complete."
echo "  Master PNG : ${MASTER_PNG}"
echo "  macOS icns : ${ICNS_OUT}"
echo "  Windows ico: ${ICO_OUT}"
echo "  Linux png  : ${MASTER_PNG} (same as master)"
echo ""
echo "Commit the generated files to version control."
