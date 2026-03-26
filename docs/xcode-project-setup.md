# VibeStudio — Xcode Project Setup

## Creating the Xcode Project

Since `.xcodeproj` is a binary/plist bundle that cannot be reliably generated
as a text file, follow these steps to create it from scratch.

### Option A: Create via Xcode UI (recommended)

1. Open Xcode 16.4+
2. File -> New -> Project
3. Select: macOS -> App
4. Configure:
   - Product Name: `VibeStudio`
   - Team: None (or Personal Team for local dev)
   - Organization Identifier: `com.vibestudio`
   - Bundle Identifier: `com.vibestudio.app`
   - Interface: SwiftUI
   - Language: Swift
   - Storage: None
   - Include Tests: Yes
5. Save to the VibeStudio directory (replace default Sources if needed)
6. Apply the configuration below

### Option B: Generate with `swift package generate-xcodeproj`

```bash
cd /path/to/VibeStudio
swift package generate-xcodeproj
```

Note: This is deprecated by Apple but still functional. Option A is preferred.

---

## Project Structure

```
VibeStudio.xcodeproj/
  project.pbxproj
  xcshareddata/
    xcschemes/
      VibeStudio.xcscheme          # Shared scheme for CI
```

## Targets

| Target             | Type              | Purpose                              |
|--------------------|-------------------|--------------------------------------|
| VibeStudio         | macOS Application | Main app target                      |
| VibeStudioTests    | Unit Test Bundle  | XCTest / Swift Testing unit tests    |

## Schemes

| Scheme         | Targets                        | Notes                        |
|----------------|--------------------------------|------------------------------|
| VibeStudio     | VibeStudio + VibeStudioTests   | Shared (checked into git)    |

**Important:** The scheme must be shared for CI to find it.
Xcode -> Product -> Scheme -> Manage Schemes -> check "Shared" checkbox.

## Build Configurations

| Configuration | Use Case                          | Optimization | Signing        |
|---------------|-----------------------------------|--------------|----------------|
| Debug         | Local development, testing        | None (-Onone)| Sign to Run Locally |
| Release       | Archive, DMG, CI builds           | -O           | See below      |

## Build Settings (apply to VibeStudio target)

### General

| Setting                      | Value                           |
|------------------------------|----------------------------------|
| PRODUCT_NAME                 | VibeStudio                       |
| PRODUCT_BUNDLE_IDENTIFIER    | com.vibestudio.app               |
| MARKETING_VERSION            | 0.1.0                            |
| CURRENT_PROJECT_VERSION      | 1                                |
| MACOSX_DEPLOYMENT_TARGET     | 14.0                             |
| SWIFT_VERSION                | 5.10                             |
| INFOPLIST_FILE               | VibeStudio/Info.plist            |
| CODE_SIGN_ENTITLEMENTS       | VibeStudio/VibeStudio.entitlements |
| GENERATE_INFOPLIST_FILE      | NO                               |
| COMBINE_HIDPI_IMAGES         | YES                              |

### Signing (MVP — unsigned)

| Setting                      | Debug                  | Release                |
|------------------------------|------------------------|------------------------|
| CODE_SIGNING_REQUIRED        | NO                     | NO                     |
| CODE_SIGNING_ALLOWED         | YES                    | NO                     |
| CODE_SIGN_IDENTITY           | Apple Development      | - (ad-hoc)             |
| CODE_SIGN_STYLE              | Automatic              | Manual                 |
| DEVELOPMENT_TEAM             | (empty or Personal)    | (empty)                |

### Signing (Future — with Apple Developer account)

| Setting                      | Release                              |
|------------------------------|--------------------------------------|
| CODE_SIGNING_REQUIRED        | YES                                  |
| CODE_SIGN_IDENTITY           | Developer ID Application             |
| CODE_SIGN_STYLE              | Manual                               |
| DEVELOPMENT_TEAM             | YOUR_TEAM_ID                         |
| OTHER_CODE_SIGN_FLAGS        | --timestamp                          |
| ENABLE_HARDENED_RUNTIME      | YES                                  |

### Swift Package Dependencies

Add via Xcode: File -> Add Package Dependencies:

| Package    | URL                                          | Version Rule    |
|------------|----------------------------------------------|-----------------|
| SwiftTerm  | https://github.com/migueldeicaza/SwiftTerm   | Up to Next Major from 1.12.0 |

### Source file groups

```
VibeStudio (target: VibeStudio)
├── App/
│   └── VibeStudioApp.swift       # @main App entry point
├── Views/                         # SwiftUI views
├── ViewModels/                    # ObservableObject view models
├── Services/                      # Git, FileWatch, Terminal services
├── Models/                        # Domain models (from Sources/Contracts/)
├── Utilities/                     # Extensions, helpers
├── Resources/
│   └── Assets.xcassets            # App icon, colors
├── Info.plist
└── VibeStudio.entitlements

VibeStudioTests (target: VibeStudioTests)
└── VibeStudioTests.swift
```

## Verification Checklist

After creating the project, verify:

- [ ] `xcodebuild -list` shows scheme "VibeStudio"
- [ ] `xcodebuild build -scheme VibeStudio` succeeds
- [ ] `make build` succeeds
- [ ] `make test` runs (even if no tests yet)
- [ ] `make archive` creates .xcarchive in build/
- [ ] `make dmg` creates .dmg in build/
- [ ] The .app launches and shows a window on macOS 14+
