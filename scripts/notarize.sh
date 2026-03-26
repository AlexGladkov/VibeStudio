#!/usr/bin/env bash
# ============================================================
# notarize.sh — PLACEHOLDER for Apple notarization
# Requires: Apple Developer account, Xcode 14+ (notarytool)
#
# Prerequisites:
#   1. Store credentials: xcrun notarytool store-credentials "AC_PASSWORD" \
#        --apple-id "you@email.com" --team-id "TEAMID" --password "app-specific-pwd"
#   2. Code sign the .app with "Developer ID Application" identity
#   3. Run this script on the DMG
#
# Usage: ./scripts/notarize.sh <dmg-path> <bundle-id>
# ============================================================

set -euo pipefail

DMG_PATH="${1:?Usage: notarize.sh <dmg-path> <bundle-id>}"
BUNDLE_ID="${2:?Usage: notarize.sh <dmg-path> <bundle-id>}"

echo "=============================================="
echo "  NOTARIZATION — NOT CONFIGURED"
echo "=============================================="
echo ""
echo "This script is a placeholder. To enable notarization:"
echo ""
echo "  1. Enroll in Apple Developer Program"
echo "  2. Create an app-specific password at appleid.apple.com"
echo "  3. Store credentials locally:"
echo "     xcrun notarytool store-credentials \"AC_PASSWORD\" \\"
echo "       --apple-id \"you@email.com\" \\"
echo "       --team-id \"YOUR_TEAM_ID\" \\"
echo "       --password \"your-app-specific-password\""
echo ""
echo "  4. Update Makefile CODE_SIGN_IDENTITY:"
echo "     CODE_SIGN_IDENTITY := Developer ID Application: Your Name (TEAMID)"
echo ""
echo "  5. Uncomment the commands below in this script."
echo ""
echo "DMG path:  ${DMG_PATH}"
echo "Bundle ID: ${BUNDLE_ID}"
echo ""

# --- Uncomment when Apple Developer account is available ---

# echo "Submitting for notarization..."
# xcrun notarytool submit "${DMG_PATH}" \
#     --keychain-profile "AC_PASSWORD" \
#     --wait

# echo "Stapling notarization ticket..."
# xcrun stapler staple "${DMG_PATH}"

# echo "Verifying notarization..."
# spctl --assess --type open --context context:primary-signature -v "${DMG_PATH}"

# echo "Notarization complete."

exit 0
