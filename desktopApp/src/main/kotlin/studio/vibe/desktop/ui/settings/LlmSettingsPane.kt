package studio.vibe.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import studio.vibe.desktop.ui.theme.DSColor
import studio.vibe.desktop.ui.theme.LocalDSColors
import studio.vibe.desktop.ui.theme.DSFont
import studio.vibe.desktop.ui.theme.DSSpacing
import studio.vibe.shared.core.common.AIAgent
import studio.vibe.shared.feature.codespeak.data.CodeSpeakPreferences
import studio.vibe.shared.feature.settings.data.GeneralPreferences

/**
 * Dispatcher pane that routes to the correct per-agent settings pane.
 *
 * Routing is now id-based so plugin agents registered at runtime are handled by
 * the [LlmGenericPane] fallback without any modification of this file.
 *
 * Mirrors Swift LLMSettingsPane.swift.
 */
@Composable
fun LlmSettingsPane(
    agent: AIAgent,
    generalPreferences: GeneralPreferences,
    codeSpeakPreferences: CodeSpeakPreferences,
    modifier: Modifier = Modifier,
) {
    when (agent.id) {
        "claude" -> ClaudeSettingsPane(preferences = generalPreferences, modifier = modifier)
        "opencode" -> OpencodeSettingsPane(agent = agent, modifier = modifier)
        "codex" -> CodexSettingsPane(agent = agent, modifier = modifier)
        "gemini" -> GeminiSettingsPane(agent = agent, modifier = modifier)
        "qwenCode" -> QwenSettingsPane(agent = agent, modifier = modifier)
        "codeSpeak" -> CodeSpeakSettingsPane(preferences = codeSpeakPreferences, modifier = modifier)
        else -> LlmGenericPane(agent = agent, modifier = modifier)
    }
}

// -- Shared LLM pane base layout -----------------------------------------------

/**
 * Base layout reused by all LLM-agent panes that follow the same structure:
 * title → description → API key section → install hint.
 *
 * Accepts [AIAgent] so plugin agents can reuse this layout without an enum entry.
 *
 * Mirrors the shared structure from Swift LLMSettingsPane sub-panes.
 */
@Composable
fun LlmBasicPane(
    agent: AIAgent,
    modifier: Modifier = Modifier,
    extraContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(DSSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.xl),
    ) {
        SettingsPaneTitle(title = agent.displayName)

        // Short description
        Text(
            text = agent.shortDescription,
            style = DSFont.bodyMedium,
            color = LocalDSColors.current.textSecondary,
        )

        // Extra content (per-agent specific sections)
        extraContent?.invoke()

        // API key or auth section
        val envVar = agent.apiKeyEnvironmentVariable
        if (envVar != null) {
            LlmApiKeySection(agent = agent, envVar = envVar)
        } else {
            LlmNoKeySection(agent = agent)
        }

        // Installation
        LlmInstallSection(agent = agent)

        Spacer(Modifier.height(DSSpacing.xl))
    }
}

/**
 * Generic fallback pane for plugin agents not handled by the built-in dispatcher.
 * Renders the standard [LlmBasicPane] layout without any agent-specific extras.
 */
@Composable
fun LlmGenericPane(
    agent: AIAgent,
    modifier: Modifier = Modifier,
) {
    LlmBasicPane(agent = agent, modifier = modifier)
}

@Composable
private fun LlmApiKeySection(agent: AIAgent, envVar: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
    ) {
        Text(
            text = "API key configuration",
            style = DSFont.buttonLabel,
            color = LocalDSColors.current.textSecondary,
        )

        Text(
            text = "Configure via environment variable:",
            style = DSFont.bodySmall,
            color = LocalDSColors.current.textSecondary,
        )

        SettingsMonoBlock(text = "export $envVar=your-key-here")

        agent.setupInstructions?.let { instructions ->
            Text(
                text = instructions,
                style = DSFont.bodySmall,
                color = LocalDSColors.current.textMuted,
            )
        }
    }
}

@Composable
private fun LlmNoKeySection(agent: AIAgent) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
    ) {
        Text(
            text = "Authentication",
            style = DSFont.buttonLabel,
            color = LocalDSColors.current.textSecondary,
        )

        SettingsCard {
            Text(
                text = agent.setupInstructions
                    ?: "No API key required — uses environment-based authentication.",
                style = DSFont.bodySmall,
                color = LocalDSColors.current.textSecondary,
                modifier = Modifier.padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
            )
        }
    }
}

@Composable
private fun LlmInstallSection(agent: AIAgent) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
    ) {
        Text(
            text = "Installation",
            style = DSFont.buttonLabel,
            color = LocalDSColors.current.textSecondary,
        )

        agent.prerequisite?.let { prereq ->
            Text(
                text = "Requires: $prereq",
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textMuted,
            )
        }

        SettingsMonoBlock(text = agent.installHint)
    }
}
