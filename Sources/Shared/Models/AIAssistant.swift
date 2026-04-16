// MARK: - AIAssistant
// Domain model for supported AI code assistants.
// macOS 14+, Swift 5.10

import Foundation

// MARK: - AgentExitSequence

/// Describes how to gracefully stop a running AI CLI agent.
enum AgentExitSequence: Sendable {
    /// Send Ctrl+C (SIGINT) only.
    case ctrlC
    /// Send Ctrl+C, wait briefly, then send a follow-up command (e.g. "/exit").
    case ctrlCThenCommand(String)
}

// MARK: - AIAssistant

/// Supported AI code assistants.
enum AIAssistant: String, CaseIterable, Identifiable, Sendable {
    case claude
    case opencode
    case codex
    case gemini
    case qwenCode
    case codeSpeak

    var id: String { rawValue }

    /// Human-readable display name for the toolbar picker.
    var displayName: String {
        switch self {
        case .claude:     return "claude"
        case .opencode:   return "opencode"
        case .codex:      return "codex"
        case .gemini:     return "gemini"
        case .qwenCode:   return "qwen"
        case .codeSpeak:  return "CodeSpeak"
        }
    }

    /// Binary name used to locate the executable via `CLIAgentPathResolver`.
    var executableName: String {
        switch self {
        case .claude:     return "claude"
        case .opencode:   return "opencode"
        case .codex:      return "codex"
        case .gemini:     return "gemini"
        case .qwenCode:   return "qwen"
        case .codeSpeak:  return "codespeak"
        }
    }

    /// Arguments passed to `startProcess(args:)`.
    ///
    /// SwiftTerm prepends `execName` as argv[0], so these are argv[1..N].
    var launchArguments: [String] {
        switch self {
        case .claude:     return ["--dangerously-skip-permissions"]
        case .opencode:   return []
        case .codex:      return []
        case .gemini:     return []
        case .qwenCode:   return []
        case .codeSpeak:  return []
        }
    }

    /// Whether to launch this agent by sending its `launchCommand` into the
    /// existing shell session rather than creating a dedicated PTY.
    ///
    /// Use for agents whose authentication relies on env vars sourced by the
    /// login shell (`.zprofile`) that are not available in VibeStudio's own
    /// process environment when launched from Finder / Dock / launchd.
    var launchViaShellInput: Bool {
        switch self {
        case .opencode:  return true
        case .codeSpeak: return true
        default:         return false
        }
    }

    /// Shell command to start this assistant in the terminal.
    ///
    /// Used by the legacy `sendInput`-based launch path (opencode).
    /// New agents use the dedicated PTY launch via `TerminalService.startAgentSession`.
    var launchCommand: String {
        switch self {
        case .claude:     return "claude --dangerously-skip-permissions\n"
        case .opencode:   return "opencode\n"
        case .codex:      return "codex\n"
        case .gemini:     return "gemini\n"
        case .qwenCode:   return "qwen\n"
        case .codeSpeak:  return "codespeak build\n"
        }
    }

    /// How to gracefully terminate this agent.
    var exitSequence: AgentExitSequence {
        switch self {
        case .claude:     return .ctrlCThenCommand("/exit")
        case .opencode:   return .ctrlC
        case .codex:      return .ctrlC
        case .gemini:     return .ctrlC
        case .qwenCode:   return .ctrlC
        case .codeSpeak:  return .ctrlC
        }
    }

    /// Environment variable name that holds the API key for this agent.
    ///
    /// Returns `nil` if the agent manages its own authentication
    /// (Claude uses OAuth login via `claude login`, opencode uses its own config).
    var apiKeyEnvironmentVariable: String? {
        switch self {
        case .claude:     return nil              // OAuth via `claude login`, no API key needed
        case .opencode:   return nil
        case .codex:      return "OPENAI_API_KEY"
        case .gemini:     return "GEMINI_API_KEY"
        case .qwenCode:   return "DASHSCOPE_API_KEY"
        case .codeSpeak:  return "ANTHROPIC_API_KEY"
        }
    }

    /// Installation hint shown in the picker when the agent is not found.
    var installHint: String {
        switch self {
        case .claude:     return "npm install -g @anthropic-ai/claude-code"
        case .opencode:   return "go install github.com/opencode-ai/opencode@latest"
        case .codex:      return "npm install -g @openai/codex"
        case .gemini:     return "npm install -g @google/gemini-cli"
        case .qwenCode:   return "npm install -g @anthropic-ai/qwen-code"
        case .codeSpeak:  return "uv tool install codespeak-cli"
        }
    }

    // MARK: - Install Wizard

    /// Short description shown in the installation wizard.
    var shortDescription: String {
        switch self {
        case .claude:
            return "Anthropic's AI coding assistant. Authenticates via browser login — no API key needed."
        case .opencode:
            return "Open-source AI coding assistant with support for multiple AI models."
        case .codex:
            return "OpenAI's Codex CLI for intelligent code generation and editing."
        case .gemini:
            return "Google Gemini CLI for AI-powered code assistance and generation."
        case .qwenCode:
            return "Alibaba's Qwen Code CLI for code generation and completion."
        case .codeSpeak:
            return "Spec-driven AI coding tool. Runs codespeak build against your project specs."
        }
    }

    /// Prerequisite runtime required before installation.
    var prerequisite: String? {
        switch self {
        case .claude, .codex, .gemini, .qwenCode:
            return "Node.js 18+"
        case .opencode:
            return "Go 1.22+"
        case .codeSpeak:
            return "uv (Python package manager)"
        }
    }

    /// Command to verify the prerequisite is installed.
    var prerequisiteCheckCommand: String? {
        switch self {
        case .claude, .codex, .gemini, .qwenCode:
            return "node --version"
        case .opencode:
            return "go version"
        case .codeSpeak:
            return "uv --version"
        }
    }

    /// Post-install setup instructions (API key, auth, etc.). `nil` if none needed.
    var setupInstructions: String? {
        switch self {
        case .claude:
            return "After installation, run `claude login` to authenticate with your Anthropic account."
        case .opencode:
            return nil
        case .codex:
            return "Set your OpenAI API key:\nexport OPENAI_API_KEY=your-key-here\n\nGet a key at: platform.openai.com → API Keys"
        case .gemini:
            return "Set your Gemini API key:\nexport GEMINI_API_KEY=your-key-here\n\nGet a key at: aistudio.google.com → API Keys"
        case .qwenCode:
            return "Set your DashScope API key:\nexport DASHSCOPE_API_KEY=your-key-here\n\nGet a key at: dashscope.console.aliyun.com"
        case .codeSpeak:
            return "1. Install uv: curl -LsSf https://astral.sh/uv/install.sh | sh\n2. uv tool install codespeak-cli\n3. Set ANTHROPIC_API_KEY in Claude settings\n4. Run codespeak init in your project"
        }
    }
}
