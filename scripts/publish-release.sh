#!/usr/bin/env bash
# ============================================================
# publish-release.sh — Local, CI-free release.
#
# Builds the Release DMG, tags HEAD, pushes the tag, and creates
# (or updates) a GitHub Release with the DMG attached + auto notes.
#
# Why local: GitHub Actions runner minutes are exhausted, so the
# tag-triggered release workflow can't run. This is the canonical
# release path. Requires the `gh` CLI, authenticated.
#
# Usage: ./scripts/publish-release.sh [version]
#   version defaults to MARKETING_VERSION in project.yml.
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "${SCRIPT_DIR}")"
cd "${PROJECT_DIR}"

VERSION="${1:-$(grep 'MARKETING_VERSION:' project.yml | head -1 | sed 's/.*: *"\(.*\)"/\1/' | tr -d ' ')}"
TAG="v${VERSION}"
DMG_SRC="build/VibeStudio.dmg"
DMG_OUT="build/VibeStudio-${VERSION}.dmg"

echo "=== VibeStudio release ${TAG} ==="

command -v gh >/dev/null 2>&1 || { echo "ERROR: gh CLI not found (brew install gh)"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "ERROR: gh not authenticated (gh auth login)"; exit 1; }

# 1. Build the Release DMG (project.yml MARKETING_VERSION is the source of truth).
echo "[1/4] Building DMG..."
make dmg VERSION="${VERSION}"

# 2. Name the artifact to match the install instructions + compute checksum.
cp "${DMG_SRC}" "${DMG_OUT}"
SHA="$(shasum -a 256 "${DMG_OUT}" | awk '{print $1}')"
echo "      ${DMG_OUT}  SHA-256: ${SHA}"

# 3. Tag HEAD (force-move if it already exists) and push the tag.
echo "[2/4] Tagging ${TAG} at HEAD and pushing..."
git tag -f "${TAG}"
git push -f origin "${TAG}"

# 4. Assemble release notes: changelog since the previous tag + install + checksum.
PREV_TAG="$(git describe --tags --abbrev=0 "${TAG}^" 2>/dev/null || true)"
if [ -n "${PREV_TAG}" ]; then
  CHANGES="$(git log --pretty='- %s' "${PREV_TAG}..${TAG}")"
else
  CHANGES="$(git log --pretty='- %s' "${TAG}")"
fi

NOTES_FILE="$(mktemp)"
cat > "${NOTES_FILE}" <<EOF
## VibeStudio v${VERSION}

### Installation
1. Download \`VibeStudio-${VERSION}.dmg\`
2. Open the DMG and drag VibeStudio to Applications
3. On first launch: right-click → Open (bypass Gatekeeper for unsigned builds)

### System Requirements
- macOS 14.0 (Sonoma) or later
- Apple Silicon or Intel Mac

### Changes${PREV_TAG:+ (since ${PREV_TAG})}
${CHANGES}

### Checksum
\`\`\`
SHA-256: ${SHA}
\`\`\`

> **Note:** This build is not notarized. For personal/development use only.
EOF

echo "[3/4] Publishing GitHub Release..."
if gh release view "${TAG}" >/dev/null 2>&1; then
  gh release edit "${TAG}" --title "VibeStudio v${VERSION}" --notes-file "${NOTES_FILE}" --latest
  gh release upload "${TAG}" "${DMG_OUT}" --clobber
else
  gh release create "${TAG}" "${DMG_OUT}" --title "VibeStudio v${VERSION}" --notes-file "${NOTES_FILE}" --latest
fi

rm -f "${NOTES_FILE}"
echo "[4/4] Done: $(gh release view "${TAG}" --json url --jq .url)"
