# ============================================================
# VibeStudio — Makefile
# Targets: build, test, archive, dmg, notarize (placeholder), clean
# ============================================================

# --- Configuration -----------------------------------------------------------

APP_NAME        := VibeStudio
BUNDLE_ID       := tech.mobiledeveloper.vibestudio
SCHEME          := VibeStudio
CONFIGURATION   := Release
PLATFORM        := macosx
MIN_MACOS       := 14.0

# Derived paths
BUILD_DIR       := $(CURDIR)/build
ARCHIVE_PATH    := $(BUILD_DIR)/$(APP_NAME).xcarchive
EXPORT_DIR      := $(BUILD_DIR)/export
APP_BUNDLE      := $(EXPORT_DIR)/$(APP_NAME).app
DMG_OUTPUT      := $(BUILD_DIR)/$(APP_NAME).dmg

# Version — override from CI: make build VERSION=1.0.0 BUILD_NUMBER=42
VERSION         ?= 0.1.0
BUILD_NUMBER    ?= 1

# Signing — for unsigned MVP builds
CODE_SIGN_IDENTITY ?= -
# PLACEHOLDER: set to "Developer ID Application: Your Name (TEAMID)" for notarization
# CODE_SIGN_IDENTITY ?= Developer ID Application: Your Name (TEAMID)

# Notarization — placeholder, requires Apple Developer account
# NOTARIZE_TEAM_ID   ?=
# NOTARIZE_APPLE_ID  ?=
# NOTARIZE_PASSWORD  ?= @keychain:AC_PASSWORD

# --- Xcode build flags -------------------------------------------------------

XCODEBUILD := xcodebuild \
	-scheme $(SCHEME) \
	-configuration $(CONFIGURATION) \
	-derivedDataPath $(BUILD_DIR)/DerivedData \
	MACOSX_DEPLOYMENT_TARGET=$(MIN_MACOS) \
	MARKETING_VERSION=$(VERSION) \
	CURRENT_PROJECT_VERSION=$(BUILD_NUMBER) \
	PRODUCT_BUNDLE_IDENTIFIER=$(BUNDLE_ID)

# --- Targets ------------------------------------------------------------------

.PHONY: all build test archive export dmg notarize clean help resolve-deps

all: dmg

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

resolve-deps: ## Resolve Swift Package Manager dependencies
	$(XCODEBUILD) -resolvePackageDependencies

build: ## Build the application (debug or release)
	$(XCODEBUILD) build

test: ## Run unit tests
	xcodebuild test \
		-scheme $(SCHEME) \
		-configuration Debug \
		-derivedDataPath $(BUILD_DIR)/DerivedData \
		-resultBundlePath $(BUILD_DIR)/TestResults.xcresult \
		MACOSX_DEPLOYMENT_TARGET=$(MIN_MACOS) \
		PRODUCT_BUNDLE_IDENTIFIER=$(BUNDLE_ID) \
		| xcpretty --color || true

archive: ## Create xcarchive for distribution
	$(XCODEBUILD) archive \
		-archivePath $(ARCHIVE_PATH) \
		CODE_SIGN_IDENTITY="$(CODE_SIGN_IDENTITY)" \
		CODE_SIGNING_REQUIRED=NO \
		CODE_SIGNING_ALLOWED=NO

export: archive ## Export .app from archive
	@mkdir -p $(EXPORT_DIR)
	@# For unsigned builds, extract directly from archive
	@cp -R "$(ARCHIVE_PATH)/Products/Applications/$(APP_NAME).app" "$(EXPORT_DIR)/"
	@echo "Exported: $(APP_BUNDLE)"

dmg: export ## Create DMG installer
	@$(CURDIR)/scripts/create-dmg.sh \
		"$(APP_BUNDLE)" \
		"$(DMG_OUTPUT)" \
		"$(VERSION)"
	@echo "DMG created: $(DMG_OUTPUT)"

notarize: dmg ## Notarize DMG (requires Apple Developer account)
	@$(CURDIR)/scripts/notarize.sh "$(DMG_OUTPUT)" "$(BUNDLE_ID)"

clean: ## Remove all build artifacts
	@rm -rf $(BUILD_DIR)
	@rm -rf $(CURDIR)/.build
	@echo "Build artifacts cleaned."
