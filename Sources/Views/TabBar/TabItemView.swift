// MARK: - TabItemView
// Individual tab in the tab bar.
// Shows project name, branch, activity indicator, close button.
// macOS 14+, Swift 5.10

import SwiftUI

/// A single tab in the tab bar representing one project.
///
/// Layout:
/// ```
/// ┌─ 12pt ─┬──────────────────────────────────┬── 4pt ──┬──────┬─ 8pt ─┐
/// │        │  [dot] project-name  branch       │         │  x   │       │
/// └────────┴──────────────────────────────────┴─────────┴──────┴───────┘
/// ```
struct TabItemView: View {

    let project: Project
    let isActive: Bool

    @Environment(\.projectManager) private var projectManager
    @Environment(\.terminalSessionManager) private var terminalManager
    @State private var isHovering = false
    @State private var pulseOpacity: Double = 1.0
    @State private var showErrorGlow = false

    /// Reads reactive `@Observable` state from `TerminalService`.
    /// Multicast-safe: every `TabItemView` sees the same value.
    private var activityState: TabActivityState {
        terminalManager.projectActivityStates[project.id] ?? .idle
    }

    var body: some View {
        HStack(spacing: DSSpacing.xs) {
            // Activity indicator.
            if !isActive {
                activityIndicator
            }

            // Project name.
            Text(project.name)
                .font(DSFont.tabTitle)
                .foregroundStyle(isActive || isHovering ? DSColor.textPrimary : DSColor.textSecondary)
                .lineLimit(1)

            // Close button.
            if isActive || isHovering {
                Button {
                    closeProject()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: DSLayout.tabCloseIconSize))
                        .foregroundStyle(DSColor.textSecondary)
                        .opacity(isActive ? 0.6 : 0.4)
                        .frame(
                            width: DSLayout.tabCloseSize,
                            height: DSLayout.tabCloseSize
                        )
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, DSLayout.tabHorizontalPadding)
        .frame(
            minWidth: DSLayout.tabMinWidth,
            maxWidth: DSLayout.tabMaxWidth,
            minHeight: DSLayout.tabHeight,
            maxHeight: DSLayout.tabHeight
        )
        .background(tabBackground)
        .clipShape(RoundedRectangle(cornerRadius: DSRadius.md))
        .overlay(alignment: .bottom) {
            if isActive {
                // Active tab accent line.
                Rectangle()
                    .fill(DSColor.accentPrimary)
                    .frame(height: 2)
            }
        }
        .onHover { hovering in
            withAnimation(.easeOut(duration: 0.08)) {
                isHovering = hovering
            }
        }
    }

    // MARK: - Subviews

    /// Tab background color based on state.
    private var tabBackground: Color {
        if isActive {
            return DSColor.surfaceTabActive
        } else if isHovering {
            return DSColor.surfaceTabHover
        } else {
            return DSColor.surfaceTabInactive
        }
    }

    /// Activity indicator dot (6pt circle).
    @ViewBuilder
    private var activityIndicator: some View {
        switch activityState {
        case .idle:
            Circle()
                .fill(DSColor.indicatorIdle)
                .frame(width: DSLayout.indicatorSize, height: DSLayout.indicatorSize)

        case .running:
            Circle()
                .fill(DSColor.indicatorRunning)
                .frame(width: DSLayout.indicatorSize, height: DSLayout.indicatorSize)
                .opacity(pulseOpacity)
                .onAppear {
                    withAnimation(
                        .easeInOut(duration: 0.6)
                        .repeatForever(autoreverses: true)
                    ) {
                        pulseOpacity = 0.4
                    }
                }
                .onDisappear {
                    pulseOpacity = 1.0
                }

        case .error:
            Circle()
                .fill(DSColor.indicatorError)
                .frame(width: DSLayout.indicatorSize, height: DSLayout.indicatorSize)
                .shadow(
                    color: showErrorGlow ? DSColor.indicatorError.opacity(0.6) : .clear,
                    radius: 4
                )
                .onAppear {
                    showErrorGlow = true
                }
                .task(id: showErrorGlow) {
                    guard showErrorGlow else { return }
                    // Remove glow after 2 seconds.
                    try? await Task.sleep(for: .seconds(2))
                    guard !Task.isCancelled else { return }
                    withAnimation(.easeOut(duration: 0.3)) {
                        showErrorGlow = false
                    }
                }

        case .hidden:
            EmptyView()
        }
    }

    // MARK: - Actions

    private func closeProject() {
        terminalManager.killAllSessions(for: project.id)
        try? projectManager.removeProject(project.id)
    }
}
