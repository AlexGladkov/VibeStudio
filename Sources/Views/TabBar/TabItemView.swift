// MARK: - TabItemView
// Individual tab in the tab bar.
// Shows project name, branch, activity indicator, close button.
// macOS 14+, Swift 5.10

import SwiftUI

/// A single tab in the tab bar representing one project.
struct TabItemView: View {

    let project: Project
    let isActive: Bool

    @Environment(\.projectManager) private var projectManager
    @Environment(\.terminalSessionManager) private var terminalManager
    /// Concrete TerminalService for @Observable-tracked property access.
    ///
    /// `terminalManager` is typed `any TerminalSessionManaging` — Swift's
    /// @Observable tracking doesn't work through existentials, so SwiftUI
    /// would never re-render the view when projectActivityStates changes.
    /// Injecting the concrete type allows withObservationTracking to register
    /// the subscription correctly.
    @Environment(TerminalService.self) private var terminalService
    @State private var isHovering = false
    @State private var pulseOpacity: Double = 1.0
    @State private var attentionOpacity: Double = 1.0
    @State private var showErrorGlow = false
    @State private var vm: TabItemViewModel?

    private var viewModel: TabItemViewModel {
        if let existing = vm { return existing }
        let created = TabItemViewModel(projectManager: projectManager, terminalManager: terminalManager)
        DispatchQueue.main.async { vm = created }
        return created
    }

    private var activityState: TabActivityState {
        terminalService.projectActivityStates[project.id] ?? .idle
    }

    var body: some View {
        HStack(spacing: DSSpacing.xs) {
            if !isActive {
                activityIndicator
            }

            Text(project.name)
                .font(DSFont.tabTitle)
                .foregroundStyle(isActive || isHovering ? DSColor.textPrimary : DSColor.textSecondary)
                .lineLimit(1)

            if isActive || isHovering {
                Button {
                    viewModel.closeProject(project.id)
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
        .onAppear {
            if vm == nil {
                vm = TabItemViewModel(projectManager: projectManager, terminalManager: terminalManager)
            }
        }
    }

    private var tabBackground: Color {
        if isActive {
            return DSColor.surfaceTabActive
        } else if isHovering {
            return DSColor.surfaceTabHover
        } else {
            return DSColor.surfaceTabInactive
        }
    }

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

        case .waitingForInput:
            Circle()
                .fill(DSColor.indicatorWaiting)
                .frame(width: DSLayout.indicatorSize, height: DSLayout.indicatorSize)
                .opacity(attentionOpacity)
                .scaleEffect(attentionOpacity > 0.5 ? 1.0 : 1.25)
                .onAppear {
                    withAnimation(
                        .easeInOut(duration: 0.35)
                        .repeatForever(autoreverses: true)
                    ) {
                        attentionOpacity = 0.1
                    }
                }
                .onDisappear {
                    attentionOpacity = 1.0
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
}
