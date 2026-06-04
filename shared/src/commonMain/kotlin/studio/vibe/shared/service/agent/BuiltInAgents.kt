package studio.vibe.shared.service.agent

import studio.vibe.shared.contract.AIAgent
import studio.vibe.shared.model.AIAssistant
import studio.vibe.shared.model.AgentExitSequence

object ClaudeAgent : AIAgent {
    override val id: String = "claude"
    override val displayName: String = "claude"
    override val executableName: String = "claude"
    override val launchCommand: String = "claude\n"
    override val exitSequence: AgentExitSequence =
        AgentExitSequence.CtrlCThenCommand("/exit")
    override val apiKeyEnvironmentVariable: String? = null
    override val installHint: String = "npm install -g @anthropic-ai/claude-code"
    override val shortDescription: String =
        "Anthropic's AI coding assistant. Authenticates via browser login — no API key needed."
    override val prerequisite: String? = "Node.js 18+"
    override val prerequisiteCheckCommand: String? = "node --version"
    override val setupInstructions: String? =
        "After installation, run `claude login` to authenticate with your Anthropic account."
    override val legacyAssistant: AIAssistant = AIAssistant.CLAUDE
}

object OpenCodeAgent : AIAgent {
    override val id: String = "opencode"
    override val displayName: String = "opencode"
    override val executableName: String = "opencode"
    override val launchViaShellInput: Boolean = true
    override val launchCommand: String = "opencode\n"
    override val apiKeyEnvironmentVariable: String? = null
    override val installHint: String = "go install github.com/opencode-ai/opencode@latest"
    override val shortDescription: String =
        "Open-source AI coding assistant with support for multiple AI models."
    override val prerequisite: String? = "Go 1.22+"
    override val prerequisiteCheckCommand: String? = "go version"
    override val setupInstructions: String? = null
    override val legacyAssistant: AIAssistant = AIAssistant.OPENCODE
}

object CodexAgent : AIAgent {
    override val id: String = "codex"
    override val displayName: String = "codex"
    override val executableName: String = "codex"
    override val launchCommand: String = "codex\n"
    override val apiKeyEnvironmentVariable: String = "OPENAI_API_KEY"
    override val installHint: String = "npm install -g @openai/codex"
    override val shortDescription: String =
        "OpenAI's Codex CLI for intelligent code generation and editing."
    override val prerequisite: String? = "Node.js 18+"
    override val prerequisiteCheckCommand: String? = "node --version"
    override val setupInstructions: String? =
        "Set your OpenAI API key:\nexport OPENAI_API_KEY=your-key-here\n\nGet a key at: platform.openai.com → API Keys"
    override val legacyAssistant: AIAssistant = AIAssistant.CODEX
}

object GeminiAgent : AIAgent {
    override val id: String = "gemini"
    override val displayName: String = "gemini"
    override val executableName: String = "gemini"
    override val launchCommand: String = "gemini\n"
    override val apiKeyEnvironmentVariable: String = "GEMINI_API_KEY"
    override val installHint: String = "npm install -g @google/gemini-cli"
    override val shortDescription: String =
        "Google Gemini CLI for AI-powered code assistance and generation."
    override val prerequisite: String? = "Node.js 18+"
    override val prerequisiteCheckCommand: String? = "node --version"
    override val setupInstructions: String? =
        "Set your Gemini API key:\nexport GEMINI_API_KEY=your-key-here\n\nGet a key at: aistudio.google.com → API Keys"
    override val legacyAssistant: AIAssistant = AIAssistant.GEMINI
}

object QwenCodeAgent : AIAgent {
    override val id: String = "qwenCode"
    override val displayName: String = "qwen"
    override val executableName: String = "qwen"
    override val launchCommand: String = "qwen\n"
    override val apiKeyEnvironmentVariable: String = "DASHSCOPE_API_KEY"
    override val installHint: String = "npm install -g @anthropic-ai/qwen-code"
    override val shortDescription: String =
        "Alibaba's Qwen Code CLI for code generation and completion."
    override val prerequisite: String? = "Node.js 18+"
    override val prerequisiteCheckCommand: String? = "node --version"
    override val setupInstructions: String? =
        "Set your DashScope API key:\nexport DASHSCOPE_API_KEY=your-key-here\n\nGet a key at: dashscope.console.aliyun.com"
    override val legacyAssistant: AIAssistant = AIAssistant.QWEN_CODE
}

object CodeSpeakAgent : AIAgent {
    override val id: String = "codeSpeak"
    override val displayName: String = "CodeSpeak"
    override val executableName: String = "codespeak"
    override val launchViaShellInput: Boolean = true
    override val launchCommand: String = "codespeak build\n"
    override val apiKeyEnvironmentVariable: String = "ANTHROPIC_API_KEY"
    override val installHint: String = "uv tool install codespeak-cli"
    override val shortDescription: String =
        "Spec-driven AI coding tool. Runs codespeak build against your project specs."
    override val prerequisite: String? = "uv (Python package manager)"
    override val prerequisiteCheckCommand: String? = "uv --version"
    override val setupInstructions: String? =
        "1. Install uv: curl -LsSf https://astral.sh/uv/install.sh | sh\n" +
            "2. uv tool install codespeak-cli\n" +
            "3. Set ANTHROPIC_API_KEY in Claude settings\n" +
            "4. Run codespeak init in your project"
    override val legacyAssistant: AIAssistant = AIAssistant.CODE_SPEAK
}

val BuiltInAgents: List<AIAgent> = listOf(
    ClaudeAgent,
    OpenCodeAgent,
    CodexAgent,
    GeminiAgent,
    QwenCodeAgent,
    CodeSpeakAgent,
)
