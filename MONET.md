---
agents:
  - vibestudio:dev:swift-dev
  - vibestudio:dev:nio-server
  - vibestudio:dev:api-designer
  - vibestudio:dev:ui-designer
  - vibestudio:dev:codespeak-runner
  - vibestudio:dev:build-tester
  - agladkov:control:*
---

# VibeStudio — MONET.md

## Project

- **Name:** VibeStudio
- **Type:** macOS 14+ Desktop Application
- **Language:** Swift 5.10
- **Frameworks:** SwiftUI + AppKit (NSViewRepresentable)
- **Min OS:** macOS 14.0 (Sonoma)
- **Build:** XcodeGen (`project.yml`) + `xcodebuild` + `make`
- **Package:** SPM (`Package.swift`)
- **Lint:** SwiftLint (`.swiftlint.yml`)
- **Distribution:** Unsigned DMG (Mac App Store not targeted)

## Architecture

| Layer | Components |
|-------|------------|
| App | VibeStudioApp, AppDelegate, AppLifecycleCoordinator, Logger |
| Features | FileTree, FileViewer, Git, GitChanges, Projects, Session, Settings, Specs, TabBar, Terminal, Toolbar |
| Views | FileViewer, Main, Settings, Sidebar, TabBar, Terminal, Toolbar |
| Services | Agent, AI, CodeSpeak, FileTree, FreeTab, General, Git, Persistence, RemoteControl, Security, Terminal, Theme, Update |
| Contracts | DependencyContainer, GitServicing, ProjectManaging, TerminalSessionManaging, FileSystemWatching, AICommitServicing, AgentAvailabilityChecking, UpdateServicing, SessionPersisting |
| DesignSystem | DSColor, DSFont, DSSpacing, DSLayout, DSRadius, DSTerminalColors, Components, Modifiers |

## Key Dependencies

- **SwiftTerm** (migueldeicaza/SwiftTerm) — PTY terminal emulator
- **SwiftNIO** (apple/swift-nio) — HTTP/WebSocket server for Remote Control
- **SwiftNIO SSL** (apple/swift-nio-ssl) — TLS for Remote Control
- **Swift Certificates** (apple/swift-certificates) — X.509 cert generation
- **Sparkle** (sparkle-project/Sparkle) — Auto-update framework
- **WebSocketKit** (vapor/websocket-kit) — WebSocket in Remote Control

## Specs (`.specs/`)

| Spec | Status |
|------|--------|
| `vibe-studio.md` — Architecture, components, models, MVP scope | Implemented |
| `design-system.md` — Full DS tokens (colors, fonts, spacing, radii, motion, a11y) | Implemented |
| `remote-control.md` — Remote Control server concept, phases, risks | Draft |
| `remote-control-api.md` — REST + WSS API v1 (full contract) | Draft |
| `codespeak-run-commands.md` — Multi-command CodeSpeak UI | Draft |

## Invariants (CLAUDE.md)

- **CodeSpeak breadcrumb:** CENTER column, LEFT-aligned, NOT in titlebar/sidebar
- **CodeSpeak controls:** RIGHT edge of titlebar (ToolbarView, trailingAnchor)
- **Version indicator:** Settings > Updates ONLY, NOT in titlebar
- **No sandbox** — app runs arbitrary shell processes
- **Keyboard-first** — Cmd+1..9, Cmd+B, Cmd+D, Cmd+W, Ctrl+Tab

## Agents

```yaml
agents:
  vibestudio:dev:swift-dev:
    role: Swift/SwiftUI/AppKit developer
    toolMode: exec
    note: Clone of voltagents:lang:swift-expert, tuned for VibeStudio stack + DS tokens + CLAUDE.md invariants
    hire-for: Feature implementation, refactoring, SwiftTerm integration, DS compliance

  vibestudio:dev:nio-server:
    role: NIO HTTP/WebSocket server specialist
    toolMode: exec
    note: Clone of voltagents:core:backend-developer, tuned for RemoteControlServer + auth + TLS
    hire-for: Remote Control server, WebSocket bridge, REST API endpoints

  vibestudio:dev:api-designer:
    role: API contract designer (REST + WSS)
    toolMode: exec
    note: Clone of voltagents:core:api-designer, tuned for Remote Control API v1 spec
    hire-for: API design, spec writing, data model mapping

  vibestudio:dev:ui-designer:
    role: SwiftUI UI/DesignSystem specialist
    toolMode: exec
    note: Clone of voltagents:core:ui-designer, tuned for DS tokens + terminal-centric layout
    hire-for: UI components, layout fixes, DS token application, visual polish

  vibestudio:dev:codespeak-runner:
    role: CodeSpeak CLI integration specialist
    toolMode: exec
    note: Created for VibeStudio. Knows CodeSpeak v0.3.9, Process integration, SIGTERM, run-commands UI
    hire-for: CodeSpeakModeView, SpecBuildPanelViewModel, CodeSpeakProcessRunner changes

  vibestudio:dev:build-tester:
    role: Build/test verification specialist
    toolMode: exec
    note: Created for VibeStudio. Runs `make build`, `make test`, `make lint`. Never claims green without running.
    hire-for: Pre-merge build verification, test runs, lint checks

  agladkov:control:ceo:
    role: Portfolio/strategy owner
    toolMode: none
    hire-for: Strategic decisions, feature prioritization, bet/roadmap

  agladkov:control:project-manager:
    role: PM orchestrator (monet-style)
    toolMode: none
    hire-for: Epic delivery, recon → decomposition → parallel execution

  agladkov:control:head-of-engineering:
    role: Engineering delivery owner
    toolMode: none
    hire-for: Green build, architecture decisions, code quality oversight

  agladkov:control:product-strategist:
    role: Product discovery
    toolMode: readonly
    hire-for: When direction unclear, competitive analysis, feature validation

  agladkov:control:hr:
    role: HR / agent roster optimizer
    toolMode: manage-agents
    hire-for: Staffing, agent creation/tuning, roster management
```

## Rules

### Stabilization
1. Before claiming a feature "done", `vibestudio:dev:build-tester` must run `make build` AND `make test` — NEVER claim green without actual run.
2. `make lint` (SwiftLint --strict) must pass before merge.
3. New warnings are regressions — fix them in the same PR.
4. `SWIFT_STRICT_CONCURRENCY: targeted` — concurrency warnings are tracked but not blocking (yet).
5. All new code must use DesignSystem tokens (DSColor, DSFont, DSSpacing, DSLayout) — no hardcoded values.

### QA
1. Each feature PR must include unit tests in `Tests/`.
2. Terminal/PTY changes: test with real shell processes (`/bin/zsh`).
3. Remote Control changes: test HTTP endpoints + WebSocket relay.
4. Git changes: test with real git repos (not mocks).
5. UI changes: verify in both light and dark appearances (dark is primary).

### Recon
1. When onboarding a new feature: read the relevant `.specs/*.md` file first.
2. Read `CLAUDE.md` invariants before any UI change.
3. Read `.specs/design-system.md` before any visual change.

### Agent Hiring Guide
- **Feature work** → `vibestudio:dev:swift-dev` (primary) + `vibestudio:dev:ui-designer` (if UI-heavy)
- **Remote Control server** → `vibestudio:dev:nio-server` + `vibestudio:dev:api-designer` (contracts)
- **CodeSpeak integration** → `vibestudio:dev:codespeak-runner`
- **Build/test verification** → `vibestudio:dev:build-tester` (always before merge)
- **Recon before any epic** → `agladkov:control:project-manager` (orchestrates recon → plan → parallel)
- **Strategy/direction** → `agladkov:control:ceo` + `agladkov:control:product-strategist`
- **Staffing needs** → `agladkov:control:hr`
