package studio.vibe.shared.model

public sealed class AgentExitSequence {
    public data object CtrlC : AgentExitSequence()
    public data class CtrlCThenCommand(val command: String) : AgentExitSequence()
}

public enum class AIAssistant(public val id: String) {
    CLAUDE("claude"),
    OPENCODE("opencode"),
    CODEX("codex"),
    GEMINI("gemini"),
    QWEN_CODE("qwenCode"),
    CODE_SPEAK("codeSpeak");

    public val displayName: String
        get() = when (this) {
            CLAUDE -> "claude"
            OPENCODE -> "opencode"
            CODEX -> "codex"
            GEMINI -> "gemini"
            QWEN_CODE -> "qwen"
            CODE_SPEAK -> "CodeSpeak"
        }

    public val executableName: String
        get() = when (this) {
            CLAUDE -> "claude"
            OPENCODE -> "opencode"
            CODEX -> "codex"
            GEMINI -> "gemini"
            QWEN_CODE -> "qwen"
            CODE_SPEAK -> "codespeak"
        }

    public val launchArguments: List<String> get() = emptyList()

    public val launchViaShellInput: Boolean
        get() = when (this) {
            OPENCODE, CODE_SPEAK -> true
            else -> false
        }

    public val launchCommand: String
        get() = when (this) {
            CLAUDE -> "claude\n"
            OPENCODE -> "opencode\n"
            CODEX -> "codex\n"
            GEMINI -> "gemini\n"
            QWEN_CODE -> "qwen\n"
            CODE_SPEAK -> "codespeak build\n"
        }

    public val exitSequence: AgentExitSequence
        get() = when (this) {
            CLAUDE -> AgentExitSequence.CtrlCThenCommand("/exit")
            else -> AgentExitSequence.CtrlC
        }

    public val apiKeyEnvironmentVariable: String?
        get() = when (this) {
            CLAUDE, OPENCODE -> null
            CODEX -> "OPENAI_API_KEY"
            GEMINI -> "GEMINI_API_KEY"
            QWEN_CODE -> "DASHSCOPE_API_KEY"
            CODE_SPEAK -> "ANTHROPIC_API_KEY"
        }

    public val installHint: String
        get() = when (this) {
            CLAUDE -> "npm install -g @anthropic-ai/claude-code"
            OPENCODE -> "go install github.com/opencode-ai/opencode@latest"
            CODEX -> "npm install -g @openai/codex"
            GEMINI -> "npm install -g @google/gemini-cli"
            QWEN_CODE -> "npm install -g @anthropic-ai/qwen-code"
            CODE_SPEAK -> "uv tool install codespeak-cli"
        }

    public val shortDescription: String
        get() = when (this) {
            CLAUDE -> "Anthropic's AI coding assistant. Authenticates via browser login — no API key needed."
            OPENCODE -> "Open-source AI coding assistant with support for multiple AI models."
            CODEX -> "OpenAI's Codex CLI for intelligent code generation and editing."
            GEMINI -> "Google Gemini CLI for AI-powered code assistance and generation."
            QWEN_CODE -> "Alibaba's Qwen Code CLI for code generation and completion."
            CODE_SPEAK -> "Spec-driven AI coding tool. Runs codespeak build against your project specs."
        }

    public val prerequisite: String?
        get() = when (this) {
            CLAUDE, CODEX, GEMINI, QWEN_CODE -> "Node.js 18+"
            OPENCODE -> "Go 1.22+"
            CODE_SPEAK -> "uv (Python package manager)"
        }

    public val prerequisiteCheckCommand: String?
        get() = when (this) {
            CLAUDE, CODEX, GEMINI, QWEN_CODE -> "node --version"
            OPENCODE -> "go version"
            CODE_SPEAK -> "uv --version"
        }

    public val setupInstructions: String?
        get() = when (this) {
            CLAUDE -> "After installation, run `claude login` to authenticate with your Anthropic account."
            OPENCODE -> null
            CODEX -> "Set your OpenAI API key:\nexport OPENAI_API_KEY=your-key-here\n\nGet a key at: platform.openai.com → API Keys"
            GEMINI -> "Set your Gemini API key:\nexport GEMINI_API_KEY=your-key-here\n\nGet a key at: aistudio.google.com → API Keys"
            QWEN_CODE -> "Set your DashScope API key:\nexport DASHSCOPE_API_KEY=your-key-here\n\nGet a key at: dashscope.console.aliyun.com"
            CODE_SPEAK -> "1. Install uv: curl -LsSf https://astral.sh/uv/install.sh | sh\n2. uv tool install codespeak-cli\n3. Set ANTHROPIC_API_KEY in Claude settings\n4. Run codespeak init in your project"
        }
}
