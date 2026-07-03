// MARK: - CodeSpeakSpecListColumn
// Left column of the 3-column CodeSpeak layout. Renders the spec tree, the
// generated-file tree and the "New Spec" footer button. Owns its own
// `specsExpanded` / `generatedExpanded` UI state; everything else flows from
// the parent ``CodeSpeakModeViewModel``.
//
// macOS 14+, Swift 5.10

import SwiftUI

/// Left-most column of the CodeSpeak mode layout.
///
/// Pure presentation — all data and actions come from the supplied
/// `vm` (``CodeSpeakModeViewModel``) and SwiftUI environment values.
struct CodeSpeakSpecListColumn: View {

    let vm: CodeSpeakModeViewModel
    let activeProject: Project?
    @Binding var showWizard: Bool

    @Environment(\.csPreferences) private var csPreferences

    @State private var specsExpanded = true
    @State private var generatedExpanded = true

    var body: some View {
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
                    DisclosureChevron(
                        isExpanded: specsExpanded,
                        font: DSFont.statusBadge,
                        useFixedFrame: true
                    )

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
                    .accessibilityLabel("Refresh")
                    .help("Refresh specs")
                }
                .padding(.leading, DSSpacing.sm)
                .padding(.trailing, DSSpacing.md)
                .frame(height: DSLayout.gitSectionHeaderHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if specsExpanded {
                specTreeBody()
            }
        }
    }

    @ViewBuilder
    private func specTreeBody() -> some View {
        if vm.specsVM.isLoading {
            HStack { Spacer(); ProgressView().scaleEffect(DSLayout.progressScaleSmall); Spacer() }
                .frame(height: DSLayout.spinnerRowHeight)
        } else if vm.specsVM.specFiles.isEmpty {
            Text("No specs found")
                .font(DSFont.sidebarItemSmall)
                .foregroundStyle(DSColor.textMuted)
                .padding(.leading, DSLayout.chevronFrameWidth * 2)
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
                        .padding(.leading, DSLayout.chevronFrameWidth * 2)
                        .padding(.vertical, DSSpacing.xs)
                }
            }
            .padding(.horizontal, DSSpacing.sm)
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
                    .fill(spec.status.color)
                    .frame(width: DSLayout.indicatorSize, height: DSLayout.indicatorSize)

                Text(spec.name)
                    .font(DSFont.sidebarItem)
                    .foregroundStyle(isSelected ? DSColor.textPrimary : DSColor.textSecondary)
                    .lineLimit(1)
                    .truncationMode(.tail)

                Spacer()

                SpecStatusBadgeView(status: spec.status)
            }
            .padding(.horizontal, DSSpacing.xs)
            .frame(height: DSLayout.gitFileRowHeight)
            .contentShape(Rectangle())
            .background(
                isSelected ? DSColor.accentPrimarySelected : Color.clear,
                in: RoundedRectangle(cornerRadius: DSRadius.sm)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Generated Tree Section

    private func generatedTreeSection() -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                withAnimation(.easeInOut(duration: 0.18)) { generatedExpanded.toggle() }
            } label: {
                HStack(spacing: DSSpacing.xs) {
                    DisclosureChevron(
                        isExpanded: generatedExpanded,
                        font: DSFont.statusBadge,
                        useFixedFrame: true
                    )

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
                    .accessibilityLabel("Refresh")
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
                isSelected ? DSColor.accentPrimarySelected : Color.clear,
                in: RoundedRectangle(cornerRadius: DSRadius.sm)
            )
        }
        .buttonStyle(.plain)
    }
}
