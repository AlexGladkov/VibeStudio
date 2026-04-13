// MARK: - SpecBuildPanelViewModel
// Runs `codespeak build` and streams output for the right-side panel.
// macOS 14+, Swift 5.10

import Foundation
import Observation
import OSLog

/// ViewModel for `SpecBuildPanelView`.
///
/// Launches `codespeak build` via `CodeSpeakProcessRunner`, streams output
/// lines into `outputLines`, and parses stats from the output.
@Observable
@MainActor
final class SpecBuildPanelViewModel {

    // MARK: - State

    /// Accumulated stdout/stderr lines from the running build.
    private(set) var outputLines: [String] = []

    /// True while `codespeak build` is running.
    private(set) var isRunning = false

    /// Exit code after completion. Nil while running or before first run.
    private(set) var exitCode: Int32?

    /// Parsed stats from the last completed build.
    private(set) var stats: SpecStats?

    // MARK: - Dependencies

    private let processRunner = CodeSpeakProcessRunner()
    private let codeSpeak: CodeSpeakService
    private let projectManager: any ProjectManaging

    // MARK: - Init

    init(codeSpeak: CodeSpeakService, projectManager: any ProjectManaging) {
        self.codeSpeak = codeSpeak
        self.projectManager = projectManager
    }

    // MARK: - Build

    /// Run `codespeak build` in the given directory and stream output.
    func runBuild(at directory: URL) async {
        guard !isRunning else { return }
        isRunning = true
        outputLines = []
        exitCode = nil
        stats = nil

        for await event in await processRunner.run(["build"], at: directory) {
            switch event {
            case .line(let line):
                outputLines.append(line)
                parseStats(from: line)
            case .exitCode(let code):
                exitCode = code
            case .error(let message):
                outputLines.append("⚠ \(message)")
            }
        }

        isRunning = false

        // Push stats to the service so TabItemView badge updates
        if let s = stats, let activeId = projectManager.activeProjectId {
            codeSpeak.updateStats(s, for: activeId)
        }
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
