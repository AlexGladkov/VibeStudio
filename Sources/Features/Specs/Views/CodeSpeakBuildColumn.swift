// MARK: - CodeSpeakBuildColumn
// Right column of the 3-column CodeSpeak layout. Renders the build process
// output (or an empty state when no build has been run yet). View identity is
// preserved across the empty/output transition via ZStack opacity to prevent
// `HSplitView` from resetting divider positions when a build starts.
//
// macOS 14+, Swift 5.10

import SwiftUI

/// Right-most column of the CodeSpeak mode layout — the build-output panel.
struct CodeSpeakBuildColumn: View {

    let vm: CodeSpeakModeViewModel

    var body: some View {
        buildOutput()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(DSColor.surfaceRaised)
    }

    // MARK: - Body

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
                                .foregroundStyle(DSColor.buildOutputColor(for: line.buildLineKind))
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

    // MARK: - Empty State

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
}
