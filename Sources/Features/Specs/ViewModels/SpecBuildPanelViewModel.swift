// MARK: - SpecBuildPanelViewModel
// Runs `codespeak build` and streams output for the right-side panel.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// ViewModel for `SpecBuildPanelView` and the build column in `CodeSpeakModeView`.
///
/// Launches codespeak CLI commands via `CodeSpeakProcessRunner`, streams output
/// lines into `outputLines`, and parses stats from the output when applicable.
@Observable
@MainActor
final class SpecBuildPanelViewModel {

    // MARK: - State

    /// Accumulated stdout/stderr lines from the running command.
    private(set) var outputLines: [String] = []

    /// True while a codespeak command is running.
    private(set) var isRunning = false

    /// Exit code after completion. Nil while running or before first run.
    private(set) var exitCode: Int32?

    /// Parsed stats from the last completed build.
    private(set) var stats: SpecStats?

    /// True when the last run was explicitly cancelled by the user.
    private(set) var wasCancelled = false

    /// Currently selected command to execute.
    var selectedCommand: CodeSpeakCommand = .build

    /// Task name for the `.task` command.
    var taskName: String = ""

    /// Change message for the `.change` command.
    var changeMessage: String = ""

    /// Whether the current state allows running the selected command.
    var canRun: Bool {
        guard !isRunning else { return false }
        switch selectedCommand {
        case .task:   return !taskName.trimmingCharacters(in: .whitespaces).isEmpty
        case .change: return !changeMessage.trimmingCharacters(in: .whitespaces).isEmpty
        default:      return true
        }
    }

    // MARK: - Dependencies

    private let processRunner = CodeSpeakProcessRunner()
    private let codeSpeak: CodeSpeakService
    private let projectManager: any ProjectManaging

    // MARK: - Init

    init(codeSpeak: CodeSpeakService, projectManager: any ProjectManaging) {
        self.codeSpeak = codeSpeak
        self.projectManager = projectManager
    }

    // MARK: - Run

    /// Run the selected codespeak command in the given directory and stream output.
    ///
    /// - Parameters:
    ///   - directory: Working directory (project root).
    ///   - specPath: Optional path to a specific spec file (from the editor).
    func run(at directory: URL, specPath: URL? = nil) async {
        guard !isRunning else { return }
        isRunning = true
        wasCancelled = false
        outputLines = []
        exitCode = nil
        stats = nil

        let specString = specPath?.path(percentEncoded: false)
        let args = selectedCommand.cliArguments(
            specPath: specString,
            taskName: taskName,
            changeMessage: changeMessage
        )

        // Header line showing the command being run
        outputLines.append("$ codespeak \(args.joined(separator: " "))")

        for await event in await processRunner.run(args, at: directory) {
            switch event {
            case .line(let line):
                outputLines.append(line)
                if selectedCommand.supportsStatsParsing {
                    parseStats(from: line)
                }
            case .exitCode(let code):
                exitCode = code
            case .error(let message):
                outputLines.append("-- \(message)")
            }
        }

        isRunning = false

        // Push stats to the service so TabItemView badge updates
        if selectedCommand.supportsStatsParsing,
           let s = stats,
           let activeId = projectManager.activeProjectId {
            codeSpeak.updateStats(s, for: activeId)
        }
    }

    // MARK: - Stop

    /// Stop the currently running command.
    func stop() {
        guard isRunning else { return }
        isRunning = false
        wasCancelled = true
        Task { await processRunner.stop() }
    }

    // MARK: - Output Parsing

    /// Parse `codespeak build` summary line: "N passing, M failing" or similar.
    private func parseStats(from line: String) {
        let lower = line.lowercased()

        // Patterns:
        // "3 passing, 1 failing"
        // "3/4 specs passing"
        // "✓ 3 specs passing"
        // "✗ 1 spec failing"

        var passing = 0
        var total = 0
        var parsed = false

        // Pattern: "N passing, M failing" / "N passing"
        if let passingMatch = lower.firstMatch(of: /(\d+)\s+passing/) {
            passing = Int(passingMatch.1) ?? 0
            total = passing
            parsed = true
        }
        if let failingMatch = lower.firstMatch(of: /(\d+)\s+failing/) {
            let failing = Int(failingMatch.1) ?? 0
            total = passing + failing
            parsed = true
        }

        // Pattern: "N/M specs passing"
        if let slashMatch = lower.firstMatch(of: /(\d+)\/(\d+)\s+specs?\s+passing/) {
            passing = Int(slashMatch.1) ?? 0
            total = Int(slashMatch.2) ?? 0
            parsed = true
        }

        if parsed && total > 0 {
            stats = SpecStats(passing: passing, total: total, buildDate: Date())
        }
    }
}
