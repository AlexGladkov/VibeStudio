// MARK: - TerminalAreaView
// Container for terminal panels with split support.
// macOS 14+, Swift 5.10

import OSLog
import SwiftUI
import UniformTypeIdentifiers

/// Container view for terminal panels.
///
/// Displays terminal sessions for the active project.
/// Supports horizontal split (Cmd+D) for multiple panels.
struct TerminalAreaView: View {

    @Environment(\.projectManager) private var projectManager
    @Environment(\.terminalSessionManager) private var terminalManager

    @State private var vm: TerminalAreaViewModel?

    private var viewModel: TerminalAreaViewModel {
        if let existing = vm { return existing }
        let created = TerminalAreaViewModel(projectManager: projectManager, terminalManager: terminalManager)
        DispatchQueue.main.async { vm = created }
        return created
    }

    var body: some View {
        Group {
            if let projectId = projectManager.activeProjectId {
                let sessions = terminalManager.sessions(for: projectId)

                if sessions.isEmpty {
                    emptyTerminalView(projectId: projectId)
                } else if sessions.count == 1, let session = sessions.first {
                    DroppableTerminalPanel(session: session, terminalManager: terminalManager)
                        .id(session.id)
                } else {
                    HSplitView {
                        ForEach(sessions) { session in
                            DroppableTerminalPanel(session: session, terminalManager: terminalManager)
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
        .onAppear {
            if vm == nil {
                vm = TerminalAreaViewModel(projectManager: projectManager, terminalManager: terminalManager)
            }
        }
    }

    @ViewBuilder
    private func emptyTerminalView(projectId: UUID) -> some View {
        VStack {
            Spacer()
            Button("New Terminal") {
                viewModel.createTerminal(for: projectId)
            }
            .buttonStyle(.plain)
            .font(DSFont.buttonLabel)
            .foregroundStyle(DSColor.accentPrimary)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            viewModel.createTerminal(for: projectId)
        }
    }
}

// MARK: - DroppableTerminalPanel

/// Wraps a terminal panel with file drag-and-drop support.
///
/// Accepts file URLs dragged from Finder or from the internal FileTree.
/// Dropped file paths are shell-escaped and sent to the PTY via sendInput.
private struct DroppableTerminalPanel: View {

    let session: TerminalSession
    let terminalManager: any TerminalSessionManaging

    @State private var isDragTarget = false

    var body: some View {
        TerminalHostView(sessionId: session.id)
            .overlay {
                if isDragTarget {
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(DSColor.accentPrimary, lineWidth: 1.5)
                        .background(
                            RoundedRectangle(cornerRadius: 4)
                                .fill(DSColor.accentPrimary.opacity(0.08))
                        )
                        .allowsHitTesting(false)
                }
            }
            .onDrop(of: [.fileURL], isTargeted: $isDragTarget) { providers in
                handleDrop(providers: providers)
            }
    }

    // MARK: - Drop Handler

    /// Extracts file paths from dropped item providers and sends them to the PTY.
    private func handleDrop(providers: [NSItemProvider]) -> Bool {
        var paths: [String] = []
        let group = DispatchGroup()
        for provider in providers {
            guard provider.canLoadObject(ofClass: URL.self) else { continue }
            group.enter()
            _ = provider.loadObject(ofClass: URL.self) { url, _ in
                if let url = url {
                    paths.append(url.path(percentEncoded: false))
                }
                group.leave()
            }
        }
        group.notify(queue: .main) {
            guard !paths.isEmpty else { return }
            let text = paths.map { shellEscape($0) }.joined(separator: " ")
            terminalManager.sendInput(text, to: session.id)
        }
        return true
    }

    // MARK: - Shell Escaping

    /// Wraps a path in single quotes if it contains shell-special characters.
    ///
    /// Single quotes inside the path are escaped as `'\''`.
    private func shellEscape(_ path: String) -> String {
        let needsQuoting = path.contains(" ") || path.contains("(") || path.contains(")") ||
                           path.contains("&") || path.contains(";") || path.contains("'") ||
                           path.contains("\"") || path.contains("\\") || path.contains("[") ||
                           path.contains("]") || path.contains("{") || path.contains("}")
        guard needsQuoting else { return path }
        let escaped = path.replacingOccurrences(of: "'", with: "'\\''")
        return "'\(escaped)'"
    }
}
