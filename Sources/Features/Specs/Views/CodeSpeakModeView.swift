// MARK: - CodeSpeakModeView
// 3-column layout for CodeSpeak mode: spec list | editor | build output.
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
    @Environment(\.codeSpeak) private var codeSpeak
    @Environment(\.navigationCoordinator) private var navigationCoordinator
    @Environment(\.syntaxParserRegistry) private var syntaxParserRegistry
    @Environment(\.csPreferences) private var csPreferences

    @State private var showWizard = false
    @State private var showingProjectPicker = false

    private var activeProject: Project? { projectManager.activeProject }

    var body: some View {
        VStack(spacing: 0) {
            HSplitView {
                specListColumn()
                    .frame(minWidth: 180, idealWidth: 220, maxWidth: 320)
                    // Report column width so ToolbarView can align breadcrumb
                    // exactly above the center column's left edge.
                    .background(GeometryReader { geo in
                        Color.clear
                            .onAppear { navigationCoordinator.specsColumnWidth = geo.size.width }
                            .onChange(of: geo.size.width) { _, w in
                                navigationCoordinator.specsColumnWidth = w
                            }
                    })

                editorColumn()
                    .frame(minWidth: 300)

                buildColumn()
                    .frame(minWidth: 240, idealWidth: 320, maxWidth: 480)
            }
        }
        .background(DSColor.surfaceBase)
        .onChange(of: projectManager.activeProjectId) { _, _ in
            if let project = activeProject {
                Task { await vm.specsVM.loadSpecs(at: project.path) }
                Task { await vm.scanGenerated(at: project.path) }
                vm.selectedSpec = nil
                vm.selectedGenerated = nil
                vm.editorContent = ""
                vm.isEditorDirty = false
            }
        }
        // Run triggered by toolbar ▶ button
        .onChange(of: navigationCoordinator.codeSpeakBuildRequested) { _, requested in
            if requested, let project = activeProject {
                navigationCoordinator.codeSpeakBuildRequested = false
                // Sync command state from coordinator (toolbar is source of truth)
                vm.buildVM.selectedCommand = navigationCoordinator.runBar.command
                vm.buildVM.taskName = navigationCoordinator.runBar.taskName
                vm.buildVM.changeMessage = navigationCoordinator.runBar.changeMessage
                Task { await vm.buildVM.run(at: project.path, specPath: vm.selectedSpec?.url) }
            }
        }
        // Stop triggered by toolbar ■ button
        .onChange(of: navigationCoordinator.runBar.stopRequested) { _, requested in
            if requested {
                navigationCoordinator.runBar.stopRequested = false
                vm.buildVM.stop()
            }
        }
        // Mirror isRunning back to coordinator so toolbar can show ■ vs ▶
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
            vm.buildVM.selectedCommand = .build
            Task { await vm.buildVM.run(at: project.path, specPath: vm.selectedSpec?.url) }
        }
        // Sync selected spec name to titlebar breadcrumb
        .onChange(of: vm.selectedSpec) { _, spec in
            navigationCoordinator.runBar.currentSpecName = spec?.name ?? ""
        }
        // Sync dirty state to titlebar dirty indicator
        .onChange(of: vm.isEditorDirty) { _, dirty in
            navigationCoordinator.runBar.isEditorDirty = dirty
        }
        .background {
            VStack {
                Button("") {
                    Task { await vm.saveCurrentSpec() }
                }
                .keyboardShortcut("s", modifiers: .command)
                .hidden()

                Button("") {
                    if let project = activeProject {
                        if vm.buildVM.isRunning {
                            vm.buildVM.stop()
                        } else {
                            vm.buildVM.selectedCommand = navigationCoordinator.runBar.command
                            vm.buildVM.taskName = navigationCoordinator.runBar.taskName
                            vm.buildVM.changeMessage = navigationCoordinator.runBar.changeMessage
                            Task { await vm.buildVM.run(at: project.path, specPath: vm.selectedSpec?.url) }
                        }
                    }
                }
                .keyboardShortcut(.return, modifiers: .command)
                .hidden()
            }
        }
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

    // MARK: - Left Column: File Tree

    @State private var specsExpanded = true
    @State private var generatedExpanded = true

    private func specListColumn() -> some View {
        VStack(spacing: 0) {
            ScrollView(.vertical, showsIndicators: true) {
                VStack(alignment: .leading, spacing: 0) {
                    specTreeSection()

                    if !vm.generatedFiles.isEmpty {
                        generatedTreeSection()
                    }
                }
                .padding(.vertical, DSSpacing.xs)
            }

            Spacer(minLength: 0)

            Divider()
            Button {
                showWizard = true
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: "plus")
                    Text("New Spec")
                }
                .font(DSFont.buttonLabel)
                .foregroundStyle(DSColor.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, DSSpacing.md)
                .padding(.vertical, DSSpacing.sm)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .background(DSColor.surfaceRaised)
        .task(id: activeProject?.id) {
            guard let project = activeProject else { return }
            await vm.specsVM.loadSpecs(at: project.path)
            await vm.scanGenerated(at: project.path)
            if vm.selectedSpec == nil, let first = vm.specsVM.specFiles.first {
                vm.selectSpec(first)
            }
            // Build on project open: run build automatically when entering a CS project.
            if csPreferences.buildOnProjectOpen && !vm.specsVM.specFiles.isEmpty {
                vm.buildVM.selectedCommand = .build
                Task { await vm.buildVM.run(at: project.path) }
            }
        }
    }

    // MARK: - Specs Tree Section

    private func specTreeSection() -> some View {
        VStack(alignment: .leading, spacing: 0) {
            // Section header row
            Button {
                withAnimation(.easeInOut(duration: 0.18)) { specsExpanded.toggle() }
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: "chevron.right")
                        .font(DSFont.statusBadge)
                        .foregroundStyle(DSColor.textMuted)
                        .rotationEffect(.degrees(specsExpanded ? 90 : 0))
                        .frame(width: DSLayout.chevronFrameWidth)

                    Text("SPECS")
                        .font(DSFont.sidebarSection)
                        .foregroundStyle(DSColor.textSecondary)

                    if let stats = vm.specsVM.stats {
                        Text("\(stats.passing)/\(stats.total)")
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(stats.allPassing ? DSColor.gitAdded : DSColor.gitModified)
                    }

                    Spacer()

                    Button {
                        if let project = activeProject {
                            Task { await vm.specsVM.refresh(at: project.path) }
                        }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.textMuted)
                    }
                    .buttonStyle(.plain)
                    .help("Refresh specs")
                }
                .padding(.leading, DSSpacing.sm)
                .padding(.trailing, DSSpacing.md)
                .frame(height: DSLayout.gitSectionHeaderHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if specsExpanded {
                if vm.specsVM.isLoading {
                    HStack { Spacer(); ProgressView().scaleEffect(0.6); Spacer() }
                        .frame(height: DSLayout.spinnerRowHeight)
                } else if vm.specsVM.specFiles.isEmpty {
                    Text("No specs found")
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)
                        .padding(.leading, 28) // 2x chevronFrameWidth
                        .padding(.vertical, DSSpacing.xs)
                } else {
                    let failingSpecs = vm.specsVM.specFiles.filter { $0.status == .failing }
                    // Show failing only if enabled and at least one result is known.
                    // Falls back to all specs when no build has run yet (all .unknown).
                    let visibleSpecs = (csPreferences.showFailingOnly && !failingSpecs.isEmpty)
                        ? failingSpecs
                        : vm.specsVM.specFiles
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(visibleSpecs) { spec in
                            specRow(spec: spec)
                        }
                        if csPreferences.showFailingOnly && failingSpecs.isEmpty
                            && vm.specsVM.specFiles.allSatisfy({ $0.status == .passing }) {
                            Text("All specs passing")
                                .font(DSFont.sidebarItemSmall)
                                .foregroundStyle(DSColor.gitAdded)
                                .padding(.leading, 28)
                                .padding(.vertical, DSSpacing.xs)
                        }
                    }
                    .padding(.horizontal, DSSpacing.sm)
                }
            }
        }
    }

    private func specRow(spec: SpecFile) -> some View {
        let isSelected = vm.selectedSpec?.id == spec.id
        return Button {
            vm.selectSpec(spec)
        } label: {
            HStack(spacing: DSSpacing.xs) {
                // indent to align with section label
                Color.clear.frame(width: DSLayout.chevronFrameWidth)

                Circle()
                    .fill(specStatusColor(spec.status))
                    .frame(width: DSLayout.indicatorSize, height: DSLayout.indicatorSize)

                Text(spec.name)
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(isSelected ? DSColor.textPrimary : DSColor.textSecondary)
                    .lineLimit(1)
                    .truncationMode(.tail)

                Spacer()

                specStatusBadge(for: spec)
            }
            .padding(.horizontal, DSSpacing.xs)
            .frame(height: DSLayout.gitFileRowHeight)
            .contentShape(Rectangle())
            .background(
                isSelected
                    ? DSColor.accentPrimary.opacity(0.12)
                    : Color.clear,
                in: RoundedRectangle(cornerRadius: DSRadius.sm)
            )
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private func specStatusBadge(for spec: SpecFile) -> some View {
        switch spec.status {
        case .passing:
            Image(systemName: "checkmark")
                .font(DSFont.statusBadge)
                .foregroundStyle(DSColor.gitAdded)
        case .failing:
            Image(systemName: "xmark")
                .font(DSFont.statusBadge)
                .foregroundStyle(DSColor.gitDeleted)
        case .unknown:
            EmptyView()
        }
    }

    private func specStatusColor(_ status: SpecStatus) -> Color {
        switch status {
        case .passing: return DSColor.gitAdded
        case .failing:  return DSColor.gitDeleted
        case .unknown:  return DSColor.indicatorIdle
        }
    }

    // MARK: - Generated Tree Section

    private func generatedTreeSection() -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                withAnimation(.easeInOut(duration: 0.18)) { generatedExpanded.toggle() }
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    Image(systemName: "chevron.right")
                        .font(DSFont.statusBadge)
                        .foregroundStyle(DSColor.textMuted)
                        .rotationEffect(.degrees(generatedExpanded ? 90 : 0))
                        .frame(width: DSLayout.chevronFrameWidth)

                    Text("GENERATED")
                        .font(DSFont.sidebarSection)
                        .foregroundStyle(DSColor.textSecondary)

                    Text("\(vm.generatedFiles.count)")
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)

                    Spacer()

                    Button {
                        if let project = activeProject {
                            Task { await vm.scanGenerated(at: project.path) }
                        }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(DSColor.textMuted)
                    }
                    .buttonStyle(.plain)
                    .help("Refresh generated files")
                }
                .padding(.leading, DSSpacing.sm)
                .padding(.trailing, DSSpacing.md)
                .frame(height: DSLayout.gitSectionHeaderHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if generatedExpanded {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(vm.generatedFiles) { file in
                        generatedRow(file: file)
                    }
                }
                .padding(.horizontal, DSSpacing.sm)
            }
        }
    }

    private func generatedRow(file: GeneratedFile) -> some View {
        let isSelected = vm.selectedGenerated?.id == file.id
        return Button {
            vm.selectGeneratedFile(file)
        } label: {
            HStack(spacing: DSSpacing.xs) {
                Color.clear.frame(width: DSLayout.chevronFrameWidth)

                Image(systemName: "doc.text")
                    .font(DSFont.sidebarItemSmall)
                    .foregroundStyle(DSColor.textMuted)

                Text(file.name)
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(isSelected ? DSColor.textPrimary : DSColor.textSecondary)
                    .lineLimit(1)
                    .truncationMode(.middle)

                Spacer()
            }
            .padding(.horizontal, DSSpacing.xs)
            .frame(height: DSLayout.gitFileRowHeight)
            .contentShape(Rectangle())
            .background(
                isSelected
                    ? DSColor.accentPrimary.opacity(0.12)
                    : Color.clear,
                in: RoundedRectangle(cornerRadius: DSRadius.sm)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Center Column: Editor

    private func editorColumn() -> some View {
        VStack(spacing: 0) {
            if let generated = vm.selectedGenerated {
                // Read-only: show file name in a minimal bar
                generatedFileHeader(file: generated)
                Divider()
                CodeSpeakEditorView(
                    text: .constant(vm.editorContent),
                    isEditable: false,
                    parserRegistry: syntaxParserRegistry,
                    fileExtension: generated.url.pathExtension
                )
                .id(generated.id)
            } else if let spec = vm.selectedSpec {
                CodeSpeakEditorView(
                    text: Binding(
                        get: { vm.editorContent },
                        set: { vm.editorContent = $0; vm.isEditorDirty = true }
                    ),
                    isEditable: true,
                    parserRegistry: syntaxParserRegistry,
                    fileExtension: "cs.md"
                )
                .id(spec.id)
            } else {
                editorEmptyState
            }
        }
        .background(DSColor.surfaceBase)
    }

    private func generatedFileHeader(file: GeneratedFile) -> some View {
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: "doc.text")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)

            Text(file.name)
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textPrimary)

            Text("read-only")
                .font(.system(size: 9, weight: .medium))
                .foregroundStyle(DSColor.textMuted)
                .padding(.horizontal, DSSpacing.xs)
                .padding(.vertical, DSSpacing.xxs)
                .background(
                    DSColor.textMuted.opacity(0.12),
                    in: RoundedRectangle(cornerRadius: DSRadius.sm)
                )

            Spacer()
        }
        .padding(.horizontal, DSSpacing.md)
        .frame(height: DSLayout.gitSectionHeaderHeight)
    }

    private var editorEmptyState: some View {
        VStack(spacing: DSSpacing.sm) {
            Image(systemName: "doc.text")
                .font(DSFont.emptyStateIconLarge)
                .foregroundStyle(DSColor.textMuted)
            Text("Select a spec to edit")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DSColor.surfaceBase)
    }

    // MARK: - Right Column: Build Output

    private func buildColumn() -> some View {
        buildOutput()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(DSColor.surfaceRaised)
    }

    private func buildHeader() -> some View {
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(DSFont.smallButtonLabel)
                .foregroundStyle(DSColor.agentCodeSpeak)

            // Show current command name (read-only — controlled from toolbar)
            Text(navigationCoordinator.runBar.command.displayName)
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textSecondary)

            Spacer()

            // PASS/FAIL badge + stats (build command only)
            if navigationCoordinator.runBar.command.supportsStatsParsing {
                if let code = vm.buildVM.exitCode {
                    Text(code == 0 ? "PASS" : "FAIL")
                        .font(DSFont.badgeSmall)
                        .foregroundStyle(code == 0 ? DSColor.gitAdded : DSColor.gitDeleted)
                        .padding(.horizontal, DSSpacing.xs)
                        .padding(.vertical, DSSpacing.xxs)
                        .background(
                            code == 0 ? DSColor.diffAddedBg : DSColor.diffDeletedBg,
                            in: RoundedRectangle(cornerRadius: DSRadius.sm)
                        )
                }

                if let stats = vm.buildVM.stats {
                    Text("\(stats.passing)/\(stats.total)")
                        .font(DSFont.iconMDMedium)
                        .foregroundStyle(stats.allPassing ? DSColor.gitAdded : DSColor.gitModified)
                }
            }
        }
        .padding(.horizontal, DSSpacing.md)
        .frame(height: DSLayout.gitSectionHeaderHeight)
    }

    private func buildOutput() -> some View {
        // ZStack preserves SwiftUI view identity for both states, preventing
        // HSplitView from resetting divider positions when a build starts.
        ZStack {
            buildEmptyState
                .opacity(vm.buildVM.outputLines.isEmpty && !vm.buildVM.isRunning ? 1 : 0)

            ScrollViewReader { proxy in
                ScrollView(.vertical, showsIndicators: true) {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(Array(vm.buildVM.outputLines.enumerated()), id: \.offset) { idx, line in
                            Text(line)
                                .font(DSFont.terminal(size: 11))
                                .foregroundStyle(buildLineColor(for: line))
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, DSSpacing.md)
                                .padding(.vertical, 1) // sub-grid vertical padding for output line
                                .id(idx)
                        }
                    }
                    .padding(.vertical, DSSpacing.xs)
                }
                .onChange(of: vm.buildVM.outputLines.count) { _, count in
                    if count > 0 {
                        withAnimation(.none) {
                            proxy.scrollTo(count - 1, anchor: .bottom)
                        }
                    }
                }
                .opacity(vm.buildVM.outputLines.isEmpty && !vm.buildVM.isRunning ? 0 : 1)
            }
        }
    }

    private var buildEmptyState: some View {
        VStack(spacing: DSSpacing.sm) {
            Image(systemName: "play.circle")
                .font(DSFont.emptyStateIconLarge)
                .foregroundStyle(DSColor.textMuted)
            Text("Run CodeSpeak to see output")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
            Text("Press \u{25B6} to \(vm.buildVM.selectedCommand.displayName.lowercased())")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textDisabled)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func buildLineColor(for line: String) -> Color {
        let lower = line.lowercased()
        if lower.contains("error") || lower.contains("fail") || line.hasPrefix("\u{26A0}") {
            return DSColor.gitDeleted
        }
        if lower.contains("pass") || lower.contains("\u{2713}") || lower.contains("\u{2714}") {
            return DSColor.gitAdded
        }
        if lower.contains("warn") {
            return DSColor.gitModified
        }
        return DSColor.textPrimary
    }
}
