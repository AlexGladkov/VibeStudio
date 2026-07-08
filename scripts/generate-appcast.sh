#!/usr/bin/env bash
# ============================================================
# generate-appcast.sh — Produce appcast.xml for Sparkle
#
# Wraps Sparkle's `generate_appcast`. Signs each DMG in the
# archives dir with the EdDSA private key and writes appcast.xml
# with enclosure URLs pointing at the GitHub Releases download.
#
# The private key comes from either:
#   - the login Keychain (local, default), or
#   - SPARKLE_ED_PRIVATE_KEY env var (CI) — written to a temp file
#     and passed via --ed-key-file.
#
# Usage:
#   ./scripts/generate-appcast.sh <archives-dir> <download-url-prefix>
#
# Example (CI):
#   ./scripts/generate-appcast.sh dist \
#     "https://github.com/AlexGladkov/VibeStudio/releases/download/v0.4.0/"
# ============================================================

set -euo pipefail

ARCHIVES_DIR="${1:?Usage: generate-appcast.sh <archives-dir> <download-url-prefix>}"
DOWNLOAD_PREFIX="${2:?Usage: generate-appcast.sh <archives-dir> <download-url-prefix>}"

# Locate the generate_appcast tool from the resolved SPM artifacts.
# Locate generate_appcast in the resolved SPM artifacts. Search only dirs that
# exist and stop at the first match — avoids `set -e` tripping on a missing
# search root, and avoids the `find | head` SIGPIPE that pipefail turns fatal.
GEN=""
for root in build/DerivedData "${HOME}/Library/Developer/Xcode/DerivedData"; do
    [ -d "${root}" ] || continue
    found=$(find "${root}" -type f -name generate_appcast -path "*sparkle*" -print -quit 2>/dev/null || true)
    if [ -n "${found}" ]; then
        GEN="${found}"
        break
    fi
done

if [ -z "${GEN}" ]; then
    echo "Error: generate_appcast not found. Run 'make resolve-deps' first." >&2
    exit 1
fi

echo "==> Using ${GEN}"

KEY_ARGS=()
CLEANUP_KEY=""
if [ -n "${SPARKLE_ED_PRIVATE_KEY:-}" ]; then
    CLEANUP_KEY="$(mktemp)"
    printf '%s' "${SPARKLE_ED_PRIVATE_KEY}" > "${CLEANUP_KEY}"
    KEY_ARGS=(--ed-key-file "${CLEANUP_KEY}")
    trap 'rm -f "${CLEANUP_KEY}"' EXIT
fi

"${GEN}" \
    "${KEY_ARGS[@]}" \
    --download-url-prefix "${DOWNLOAD_PREFIX}" \
    "${ARCHIVES_DIR}"

echo "==> appcast written to ${ARCHIVES_DIR}/appcast.xml"
