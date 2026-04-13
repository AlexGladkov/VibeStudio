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

    @State private var showWizard = false

    private var activeProject: Project? {
        projectManager.projects.first { $0.id == projectManager.activeProjectId }
    }

    var body: some View {
        VStack(spacing: 0) {
            HSplitView {
                specListColumn()
                    .frame(minWidth: 180, idealWidth: 220, maxWidth: 320)

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
                vm.selectedSpec = nil
                vm.editorContent = ""
                vm.isEditorDirty = false
            }
        }
        .onChange(of: navigationCoordinator.codeSpeakBuildRequested) { _, requested in
            if requested, let project = activeProject {
                navigationCoordinator.codeSpeakBuildRequested = false
                Task { await vm.buildVM.runBuild(at: project.path) }
            }
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
                        Task { await vm.buildVM.runBuild(at: project.path) }
                    }
                }
                .keyboardShortcut(.return, modifiers: .command)
                .hidden()
            }
        }
    }

    // MARK: - Left Column: Spec List

    private func specListColumn() -> some View {
        VStack(spacing: 0) {
            specListHeader()
            Divider()

            if let project = activeProject {
                if vm.specsVM.isLoading {
                    specListLoading
                } else if vm.specsVM.specFiles.isEmpty {
                    specListEmpty
                } else {
                    specListRows()
                }
            } else {
                specListNoProject
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
                .font(.system(size: 12))
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
            // Auto-select first spec so the editor isn't blank on open
            if vm.selectedSpec == nil, let first = vm.specsVM.specFiles.first {
                vm.selectSpec(first)
            }
        }
    }

    private func specListHeader() -> some View {
        HStack(spacing: DSSpacing.xs) {
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: DSSpacing.xs) {
                    Text("SPECS")
                        .font(DSFont.sidebarSection)
                        .foregroundStyle(DSColor.textSecondary)

                    if let stats = vm.specsVM.stats {
                        Text("\(stats.passing)/\(stats.total)")
                            .font(DSFont.sidebarItemSmall)
                            .foregroundStyle(stats.allPassing ? DSColor.gitAdded : DSColor.gitModified)
                    }
                }

                if let project = activeProject {
                    Text(project.name)
                        .font(DSFont.sidebarItemSmall)
                        .foregroundStyle(DSColor.textMuted)
                        .lineLimit(1)
                }
            }

            Spacer()

            Button {
                if let project = activeProject {
                    Task { await vm.specsVM.refresh(at: project.path) }
                }
            } label: {
                Image(systemName: "arrow.clockwise")
                    .font(.system(size: 12))
                    .foregroundStyle(DSColor.textMuted)
            }
            .buttonStyle(.plain)
            .help("Refresh specs")
        }
        .padding(.horizontal, DSSpacing.md)
        .frame(height: DSLayout.gitSectionHeaderHeight)
    }

    private func specListRows() -> some View {
        ScrollView(.vertical, showsIndicators: true) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(vm.specsVM.specFiles) { spec in
                    specRow(spec: spec)
                }
            }
            .padding(.horizontal, DSSpacing.sm)
            .padding(.vertical, DSSpacing.xs)
        }
    }

    private func specRow(spec: SpecFile) -> some View {
        let isSelected = vm.selectedSpec?.id == spec.id
        return Button {
            vm.selectSpec(spec)
        } label: {
            HStack(spacing: DSSpacing.xs) {
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
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(DSColor.gitAdded)
        case .failing:
            Image(systemName: "xmark")
                .font(.system(size: 10, weight: .semibold))
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

    private var specListLoading: some View {
        VStack { Spacer(); ProgressView().scaleEffect(0.7); Spacer() }
            .frame(maxWidth: .infinity)
    }

    private var specListEmpty: some View {
        VStack(spacing: DSSpacing.sm) {
            Spacer()
            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(size: 24))
                .foregroundStyle(DSColor.textMuted)
            Text("No specs found")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
            Text("spec/*.cs.md")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted.opacity(0.6))
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private var specListNoProject: some View {
        VStack {
            Spacer()
            Text("No project selected")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Center Column: Editor

    private func editorColumn() -> some View {
        VStack(spacing: 0) {
            editorHeader()
            Divider()

            if let spec = vm.selectedSpec {
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

    private func editorHeader() -> some View {
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: "doc.text")
                .font(.system(size: 11))
                .foregroundStyle(DSColor.textMuted)

            Text(vm.selectedSpec?.name ?? "Editor")
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textPrimary)

            if vm.isEditorDirty {
                Circle()
                    .fill(DSColor.gitModified)
                    .frame(width: 6, height: 6)
            }

            Spacer()

            if vm.isEditorDirty {
                Button("Save") {
                    Task { await vm.saveCurrentSpec() }
                }
                .buttonStyle(.plain)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(DSColor.accentPrimary)
            }
        }
        .padding(.horizontal, DSSpacing.md)
        .frame(height: DSLayout.gitSectionHeaderHeight)
    }

    private var editorEmptyState: some View {
        VStack(spacing: DSSpacing.sm) {
            Image(systemName: "doc.text")
                .font(.system(size: 32))
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
        VStack(spacing: 0) {
            buildHeader()
            Divider()
            buildOutput()
        }
        .background(DSColor.surfaceRaised)
    }

    private func buildHeader() -> some View {
        HStack(spacing: DSSpacing.xs) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(DSColor.agentCodeSpeak)

            Text("CodeSpeak Build")
                .font(DSFont.sidebarSection)
                .foregroundStyle(DSColor.textPrimary)

            Spacer()

            if let code = vm.buildVM.exitCode {
                Text(code == 0 ? "PASS" : "FAIL")
                    .font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(code == 0 ? DSColor.gitAdded : DSColor.gitDeleted)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(
                        code == 0 ? DSColor.diffAddedBg : DSColor.diffDeletedBg,
                        in: RoundedRectangle(cornerRadius: 3)
                    )
            }

            if let stats = vm.buildVM.stats {
                Text("\(stats.passing)/\(stats.total)")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(stats.allPassing ? DSColor.gitAdded : DSColor.gitModified)
            }

            Button {
                guard let project = activeProject else { return }
                Task { await vm.buildVM.runBuild(at: project.path) }
            } label: {
                if vm.buildVM.isRunning {
                    ProgressView()
                        .scaleEffect(0.5)
                        .frame(width: 16, height: 16)
                } else {
                    Image(systemName: "play.fill")
                        .font(.system(size: 11))
                        .foregroundStyle(DSColor.actionRun)
                        .frame(width: 16, height: 16)
                }
            }
            .buttonStyle(.plain)
            .disabled(vm.buildVM.isRunning || activeProject == nil)
            .help("Run codespeak build")
        }
        .padding(.horizontal, DSSpacing.md)
        .frame(height: DSLayout.gitSectionHeaderHeight)
    }

    private func buildOutput() -> some View {
        Group {
            if vm.buildVM.outputLines.isEmpty && !vm.buildVM.isRunning {
                buildEmptyState
            } else {
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
                                    .padding(.vertical, 1)
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
                }
            }
        }
    }

    private var buildEmptyState: some View {
        VStack(spacing: DSSpacing.sm) {
            Image(systemName: "play.circle")
                .font(.system(size: 32))
                .foregroundStyle(DSColor.textMuted)
            Text("Run CodeSpeak to see output")
                .font(DSFont.sidebarItem)
                .foregroundStyle(DSColor.textMuted)
            Text("Press > to build specs")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted.opacity(0.6))
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
