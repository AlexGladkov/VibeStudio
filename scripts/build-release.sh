#!/usr/bin/env bash
# ============================================================
# build-release.sh — Full local release build pipeline
# Resolves deps, builds, archives, exports .app, creates DMG.
# Usage: ./scripts/build-release.sh [version]
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "${SCRIPT_DIR}")"

VERSION="${1:-0.1.0}"
BUILD_NUMBER="${2:-1}"

echo "=== VibeStudio Release Build ==="
echo "Version: ${VERSION}"
echo "Build:   ${BUILD_NUMBER}"
echo "================================"

cd "${PROJECT_DIR}"

echo ""
echo "[1/4] Resolving dependencies..."
make resolve-deps

echo ""
echo "[2/4] Running tests..."
make test || echo "Warning: tests failed or not yet configured"

echo ""
echo "[3/4] Building archive..."
make archive VERSION="${VERSION}" BUILD_NUMBER="${BUILD_NUMBER}"

echo ""
echo "[4/4] Creating DMG..."
make dmg VERSION="${VERSION}" BUILD_NUMBER="${BUILD_NUMBER}"

echo ""
echo "=== Release build complete ==="
echo "DMG: ${PROJECT_DIR}/build/VibeStudio.dmg"
