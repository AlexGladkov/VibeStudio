// MARK: - CodeSpeakCommand
// Enum of all codespeak CLI commands supported in VibeStudio.
// macOS 14+, Swift 5.10

import Foundation

/// A codespeak CLI command that can be executed from the build panel.
///
/// Each case maps to a top-level subcommand of the `codespeak` binary.
/// Use ``cliArguments(specPath:taskName:changeMessage:)`` to produce the
/// argument array for `CodeSpeakProcessRunner`.
enum CodeSpeakCommand: String, CaseIterable, Identifiable, Sendable {

    /// Validate specs against source code.
    case build

    /// Execute spec implementation (run generated code).
    case run

    /// Generate implementation from spec.
    case impl

    /// Run generated tests.
    case test

    /// Execute a named task defined in a spec.
    case task

    /// Record a change message against a spec.
    case change

    // MARK: - Identifiable

    var id: String { rawValue }

    // MARK: - Display

    /// Human-readable label for UI display.
    var displayName: String {
        switch self {
        case .build:  return "Build"
        case .run:    return "Run"
        case .impl:   return "Impl"
        case .test:   return "Test"
        case .task:   return "Task"
        case .change: return "Change"
        }
    }

    // MARK: - Input Requirements

    /// Whether the command requires a text input field (task name or change message).
    var requiresInput: Bool {
        switch self {
        case .task, .change: return true
        default: return false
        }
    }

    /// Label for the text input field when ``requiresInput`` is `true`.
    var inputLabel: String {
        switch self {
        case .task:   return "Task"
        case .change: return "Message"
        default:      return ""
        }
    }

    /// Placeholder for the text input field when ``requiresInput`` is `true`.
    var inputPlaceholder: String {
        switch self {
        case .task:   return "Task name..."
        case .change: return "Describe the change..."
        default:      return ""
        }
    }

    /// Whether this command produces stats output that can be parsed (passing/failing).
    var supportsStatsParsing: Bool {
        switch self {
        case .build: return true
        default:     return false
        }
    }

    // MARK: - CLI Arguments

    /// Build the argument array for `CodeSpeakProcessRunner.run(_:at:env:)`.
    ///
    /// All commands include `--no-interactive` to prevent stdin prompts.
    /// Input strings are capped to prevent excessively long arguments.
    ///
    /// - Parameters:
    ///   - specPath: Optional path to a specific spec file.
    ///   - taskName: Task name (used only for `.task` command).
    ///   - changeMessage: Change description (used only for `.change` command).
    /// - Returns: Array of CLI arguments (without the binary name).
    func cliArguments(specPath: String? = nil, taskName: String = "", changeMessage: String = "") -> [String] {
        switch self {
        case .task:
            let sanitizedTask = String(taskName.prefix(256))
            var args = ["task", "--no-interactive", "--", sanitizedTask]
            if let spec = specPath {
                args.append(contentsOf: ["--spec", spec])
            }
            return args

        case .change:
            let sanitizedMessage = String(changeMessage.prefix(2000))
            var args = ["change", "--no-interactive"]
            if let spec = specPath {
                args.append(spec)
            }
            args.append(contentsOf: ["-m", sanitizedMessage])
            return args

        default:
            var args = [rawValue, "--no-interactive"]
            if let spec = specPath {
                args.append(spec)
            }
            return args
        }
    }
}
