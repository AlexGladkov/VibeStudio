// MARK: - TerminalAreaView
// Container for terminal panels with split support.
// macOS 14+, Swift 5.10

import OSLog
import SwiftUI

/// Container view for terminal panels.
///
/// Displays terminal sessions for the active project.
/// Supports horizontal split (Cmd+D) for multiple panels.
struct TerminalAreaView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.terminalSessionManager) private var terminalManager

    @State private var isCreatingTerminal = false

    var body: some View {
        Group {
            if let projectId = projectManager.activeProjectId {
                let sessions = terminalManager.sessions(for: projectId)

                if sessions.isEmpty {
                    emptyTerminalView(projectId: projectId)
                } else if sessions.count == 1, let session = sessions.first {
                    TerminalHostView(sessionId: session.id)
                        .id(session.id)
                } else {
                    // Multiple sessions: horizontal split.
                    HSplitView {
                        ForEach(sessions) { session in
                            TerminalHostView(sessionId: session.id)
                                .id(session.id)
                                .frame(minWidth: DSLayout.splitMinPanelSize)
                        }
                    }
                }
            } else {
                Color.clear
            }
        }
        .background(DSColor.surfaceBase)
    }

    // MARK: - Empty State

    @ViewBuilder
    private func emptyTerminalView(projectId: UUID) -> some View {
        VStack {
            Spacer()
            Button("New Terminal") {
                createTerminal(projectId: projectId)
            }
            .buttonStyle(.plain)
            .font(DSFont.buttonLabel)
            .foregroundStyle(DSColor.accentPrimary)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            createTerminal(projectId: projectId)
        }
    }

    private func createTerminal(projectId: UUID) {
        guard !isCreatingTerminal else { return }
        isCreatingTerminal = true
        defer { isCreatingTerminal = false }
        let workingDirectory = projectManager.project(for: projectId)?.path
        do {
            try terminalManager.createSession(for: projectId, workingDirectory: workingDirectory)
        } catch {
            Logger.terminal.error("Failed to create terminal: \(error.localizedDescription, privacy: .public)")
        }
    }
}
