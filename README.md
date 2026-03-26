# VibeStudio

A macOS terminal-first IDE for developers who live in the command line. Built for those who juggle multiple projects simultaneously and want git context without leaving their workflow.

> **Terminal is the center. Everything else is context.**

![Platform](https://img.shields.io/badge/platform-macOS%2014%2B-blue)
![Swift](https://img.shields.io/badge/Swift-5.10-orange)
![License](https://img.shields.io/badge/license-PolyForm%20Non--Commercial-lightgrey)

---

## What it is

VibeStudio is a lightweight macOS desktop app that puts a real PTY terminal front and center, with a sidebar for file browsing and git operations. Think of it as a terminal emulator that actually understands your project structure.

Inspired by the speed of Neovim, the project management of JetBrains, and the aesthetic of Cursor.

---

## Features

### Multi-project tab bar
Switch between projects instantly. Each tab shows the project name, current git branch, and an activity indicator — a colored dot that tells you if a process is running, finished successfully, or exited with an error.

### Sidebar
- **Files** — file tree with git status indicators (`M`, `A`, `D`, `?`) colored like JetBrains. Double-click opens in `$EDITOR` via terminal.
- **Git** — current branch, ahead/behind counts, staged/unstaged file list, commit field, push/pull buttons. No GUI bloat.

### Real terminal
Powered by [SwiftTerm](https://github.com/migueldeicaza/SwiftTerm) — a proper PTY emulator with xterm-256color, true color, ligatures, scrollback (10,000 lines), and mouse reporting. Not a fake terminal. Not a web view.

### Keyboard-first

| Shortcut | Action |
|---|---|
| `Cmd+T` | Open project / new tab |
| `Cmd+W` | Close tab |
| `Cmd+1–9` | Switch to tab N |
| `Cmd+D` | Split terminal |
| `Cmd+B` | Toggle sidebar |
| `Cmd+K` | Clear terminal |
| `Ctrl+Tab` | Next tab |

### Session restore
Reopen the app and all your projects are back. Terminal sessions restart automatically (PTY can't be serialized), but scrollback history is preserved.

---

## Requirements

- macOS 14.0 (Sonoma) or later
- Apple Silicon or Intel Mac

---

## Installation

> Distributed as a DMG — no Mac App Store sandbox restrictions.

1. Download the latest `.dmg` from [Releases](../../releases)
2. Open the `.dmg` and drag VibeStudio to `/Applications`
3. Launch and open your first project with `Cmd+T`

---

## Building from source

```bash
# Clone the repo
git clone https://github.com/AlexGladkov/VibeStudio.git
cd VibeStudio

# Generate the Xcode project
xcodegen generate

# Build
xcodebuild -scheme VibeStudio -destination 'platform=macOS' build
```

**Dependencies** (via Swift Package Manager):
- [`migueldeicaza/SwiftTerm`](https://github.com/migueldeicaza/SwiftTerm) — PTY terminal emulator

---

## Architecture

```
Sources/
├── App/              # Entry point, AppDelegate (Composition Root)
├── Contracts/        # Protocols, models, DI container
├── DesignSystem/     # Design tokens (colors, typography, spacing)
├── Services/
│   ├── Terminal/     # PTY lifecycle, SwiftTerm integration
│   ├── Git/          # git CLI subprocess wrapper
│   └── Persistence/  # ProjectStore, SessionStore (JSON)
└── Views/
    ├── Sidebar/      # File tree, git panel, project list
    ├── TabBar/       # Project tabs
    ├── Toolbar/      # Top toolbar
    └── Main/         # Terminal area, welcome screen
```

- **SwiftUI + AppKit hybrid** — SwiftUI for layout, `NSViewRepresentable` for the terminal
- **`@Observable` + `@MainActor`** for UI-observed services
- **`actor`** for background services (git, session storage)
- **DI via `@Environment`** — no third-party DI framework

---

## Roadmap

- [x] Multi-project tabs
- [x] Embedded PTY terminal (SwiftTerm)
- [x] File tree with git status
- [x] Git panel (branch, diff, stage, commit, push, pull)
- [x] Session restore
- [x] Keyboard shortcuts
- [ ] Vertical terminal split
- [ ] Fuzzy file finder (`Cmd+P`)
- [ ] Custom themes
- [ ] SSH remote connections
- [ ] Git commit graph

---

## License

VibeStudio is free for personal, educational, and non-commercial use.
Commercial use is not permitted. See [LICENSE](./LICENSE) for details.

© 2026 Alex Gladkov
