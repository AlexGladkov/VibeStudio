// MARK: - CodeSpeakModeView
// Orchestrator for the 3-column CodeSpeak mode layout.
//
// After ARCH-M2 the spec list / editor / build columns each live in their
// own file (`CodeSpeakSpecListColumn`, `CodeSpeakEditorColumn`,
// `CodeSpeakBuildColumn`). This view is now responsible only for:
//   1. Composing the `HSplitView` with column constraints.
//   2. Reporting the spec-list column width to `AppNavigationCoordinator`
//      so the titlebar breadcrumb aligns above the centre column (see
//      CLAUDE.md "CodeSpeak Titlebar Layout" invariant).
//   3. Hosting hidden keyboard-shortcut buttons (⌘S save, ⌘↩ run/stop).
//   4. Wiring up coordinator-driven side effects (run/stop requested,
//      auto-build on save, breadcrumb sync). Heavy lifting is delegated to
//      `CodeSpeakModeViewModel` so this body stays declarative.
//
// macOS 14+, Swift 5.10

import SwiftUI

/// Full-window CodeSpeak mode layout replacing the terminal/sidebar combo.
///
/// ```
/// ┌──────────────────┬─────────────────────────┬───────────────────────┐
/// │ SPECS [5/7] [R+] │  spec/auth.cs.md [Save] │  CodeSpeak Build      │
/// ├──────────────────┤                          │  [> Run]  exit:0      │
/// │ * auth.cs.md  ok │  MarkdownEditorView      ├───────────────────────┤
/// │ * pay.cs.md   x  │  (full height)           │  > 5 specs passing    │
/// └──────────────────┴─────────────────────────┴───────────────────────┘
/// ```
struct CodeSpeakModeView: View {

    let vm: CodeSpeakModeViewModel

    @Environment(\.projectManager) private var projectManager
    @Environment(\.navigationCoordinator) private var navigationCoordinator
    @Environment(\.csPreferences) private var csPreferences

    @State private var showWizard = false

    private var activeProject: Project? { projectManager.activeProject }

    var body: some View {
        VStack(spacing: 0) {
            HSplitView {
                CodeSpeakSpecListColumn(
                    vm: vm,
                    activeProject: activeProject,
                    showWizard: $showWizard
                )
                .frame(
                    minWidth: DSLayout.codeSpeakSpecsColumnMin,
                    idealWidth: DSLayout.codeSpeakSpecsColumnIdeal,
                    maxWidth: DSLayout.codeSpeakSpecsColumnMax
                )
                // Report column width so ToolbarView can align breadcrumb
                // exactly above the center column's left edge.
                .background(GeometryReader { geo in
                    Color.clear
                        .onAppear { navigationCoordinator.specsColumnWidth = geo.size.width }
                        .onChange(of: geo.size.width) { _, w in
                            navigationCoordinator.specsColumnWidth = w
                        }
                })

                CodeSpeakEditorColumn(vm: vm)
                    .frame(minWidth: DSLayout.codeSpeakEditorColumnMin)

                CodeSpeakBuildColumn(vm: vm)
                    .frame(
                        minWidth: DSLayout.codeSpeakBuildColumnMin,
                        idealWidth: DSLayout.codeSpeakBuildColumnIdeal,
                        maxWidth: DSLayout.codeSpeakBuildColumnMax
                    )
            }
        }
        .background(DSColor.surfaceBase)
        .modifier(CodeSpeakCoordinationModifier(
            vm: vm,
            activeProject: activeProject,
            navigationCoordinator: navigationCoordinator,
            csPreferences: csPreferences
        ))
        .background { keyboardShortcuts }
        .sheet(isPresented: $showWizard) {
            if let project = activeProject {
                SpecWizardSheet(projectPath: project.path) {
                    if let project = activeProject {
                        Task { await vm.specsVM.refresh(at: project.path) }
                        Task { await vm.scanGenerated(at: project.path) }
                    }
                }
            }
        }
    }

    // MARK: - Hidden Keyboard Shortcuts

    private var keyboardShortcuts: some View {
        VStack {
            Button("") {
                Task { await vm.saveCurrentSpec() }
            }
            .keyboardShortcut("s", modifiers: .command)
            .hidden()

            Button("") {
                guard let project = activeProject else { return }
                if vm.buildVM.isRunning {
                    vm.buildVM.stop()
                } else {
                    Task {
                        await vm.runBuild(
                            at: project.path,
                            specURL: vm.selectedSpec?.url,
                            runBar: navigationCoordinator.runBar
                        )
                    }
                }
            }
            .keyboardShortcut(.return, modifiers: .command)
            .hidden()
        }
    }
}

// MARK: - Coordination ViewModifier (ARCH-M3)

/// Encapsulates the `.onChange` side effects that synchronise the
/// `CodeSpeakModeViewModel` with `AppNavigationCoordinator` and
/// `CodeSpeakPreferences`. Extracted from `CodeSpeakModeView` so the body
/// reads as a pure layout while the cross-cutting plumbing lives in one
/// focused modifier.
private struct CodeSpeakCoordinationModifier: ViewModifier {

    let vm: CodeSpeakModeViewModel
    let activeProject: Project?
    let navigationCoordinator: AppNavigationCoordinator
    let csPreferences: CodeSpeakPreferences

    func body(content: Content) -> some View {
        titlebarSync(applyBuildLifecycle(to: content))
    }

    /// Reset/reload on project switch + drive build lifecycle from toolbar and
    /// preferences (run, stop, isRunning side effects, auto-build on save).
    private func applyBuildLifecycle<V: View>(to content: V) -> some View {
        content
            // Project switch — reset editor state and reload trees.
            .onChange(of: activeProject?.id) { _, _ in
                if let project = activeProject {
                    vm.resetForProjectSwitch()
                    Task { await vm.reload(projectPath: project.path) }
                }
            }
            // Run triggered by toolbar ▶ button.
            .onChange(of: navigationCoordinator.codeSpeakBuildRequested) { _, requested in
                guard requested, let project = activeProject else { return }
                navigationCoordinator.codeSpeakBuildRequested = false
                Task {
                    await vm.runBuild(
                        at: project.path,
                        specURL: vm.selectedSpec?.url,
                        runBar: navigationCoordinator.runBar
                    )
                }
            }
            // Stop triggered by toolbar ■ button.
            .onChange(of: navigationCoordinator.runBar.stopRequested) { _, requested in
                if requested {
                    navigationCoordinator.runBar.stopRequested = false
                    vm.buildVM.stop()
                }
            }
            // Mirror isRunning back to coordinator + handle side effects.
            .onChange(of: vm.buildVM.isRunning) { old, running in
                navigationCoordinator.runBar.isRunning = running
                // Auto-open build panel (Regular mode right panel) when command starts.
                if running && csPreferences.autoOpenBuildPanel {
                    withAnimation(.easeOut(duration: 0.2)) {
                        navigationCoordinator.showingSpecPanel = true
                    }
                }
                // Notify on complete (command finished, not cancelled).
                if old && !running {
                    csPreferences.sendCompletionNotification(
                        command: vm.buildVM.selectedCommand,
                        exitCode: vm.buildVM.exitCode,
                        wasCancelled: vm.buildVM.wasCancelled
                    )
                }
            }
            // Auto-build on save: always run `build` after a successful spec save.
            .onChange(of: vm.savedAt) { _, _ in
                guard csPreferences.autoBuildOnSave,
                      let project = activeProject,
                      !vm.buildVM.isRunning else { return }
                Task {
                    await vm.runDefaultBuild(at: project.path, specURL: vm.selectedSpec?.url)
                }
            }
    }

    /// Mirror editor selection/dirty state into the titlebar run bar.
    private func titlebarSync<V: View>(_ content: V) -> some View {
        content
            // Sync selected spec name to titlebar breadcrumb.
            .onChange(of: vm.selectedSpec) { _, spec in
                navigationCoordinator.runBar.currentSpecName = spec?.name ?? ""
            }
            // Sync dirty state to titlebar dirty indicator.
            .onChange(of: vm.isEditorDirty) { _, dirty in
                navigationCoordinator.runBar.isEditorDirty = dirty
            }
    }
}
