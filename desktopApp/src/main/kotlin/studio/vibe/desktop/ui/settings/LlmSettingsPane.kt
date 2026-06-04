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
import studio.vibe.shared.model.AIAssistant
import studio.vibe.shared.preferences.CodeSpeakPreferences
import studio.vibe.shared.preferences.GeneralPreferences

/**
 * Dispatcher pane that routes to the correct per-agent settings pane.
 *
 * Mirrors Swift LLMSettingsPane.swift.
 */
@Composable
fun LlmSettingsPane(
    assistant: AIAssistant,
    generalPreferences: GeneralPreferences,
    codeSpeakPreferences: CodeSpeakPreferences,
    modifier: Modifier = Modifier,
) {
    when (assistant) {
        AIAssistant.CLAUDE -> ClaudeSettingsPane(preferences = generalPreferences, modifier = modifier)
        AIAssistant.OPENCODE -> OpencodeSettingsPane(modifier = modifier)
        AIAssistant.CODEX -> CodexSettingsPane(modifier = modifier)
        AIAssistant.GEMINI -> GeminiSettingsPane(modifier = modifier)
        AIAssistant.QWEN_CODE -> QwenSettingsPane(modifier = modifier)
        AIAssistant.CODE_SPEAK -> CodeSpeakSettingsPane(preferences = codeSpeakPreferences, modifier = modifier)
    }
}

// -- Shared LLM pane base layout -----------------------------------------------

/**
 * Base layout reused by all LLM-agent panes that follow the same structure:
 * title → description → API key section → install hint.
 *
 * Mirrors the shared structure from Swift LLMSettingsPane sub-panes.
 */
@Composable
fun LlmBasicPane(
    assistant: AIAssistant,
    modifier: Modifier = Modifier,
    extraContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(DSSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.xl),
    ) {
        SettingsPaneTitle(title = assistant.displayName)

        // Short description
        Text(
            text = assistant.shortDescription,
            style = DSFont.bodyMedium,
            color = LocalDSColors.current.textSecondary,
        )

        // Extra content (per-agent specific sections)
        extraContent?.invoke()

        // API key or auth section
        val envVar = assistant.apiKeyEnvironmentVariable
        if (envVar != null) {
            LlmApiKeySection(assistant = assistant, envVar = envVar)
        } else {
            LlmNoKeySection(assistant = assistant)
        }

        // Installation
        LlmInstallSection(assistant = assistant)

        Spacer(Modifier.height(DSSpacing.xl))
    }
}

@Composable
private fun LlmApiKeySection(assistant: AIAssistant, envVar: String) {
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

        assistant.setupInstructions?.let { instructions ->
            Text(
                text = instructions,
                style = DSFont.bodySmall,
                color = LocalDSColors.current.textMuted,
            )
        }
    }
}

@Composable
private fun LlmNoKeySection(assistant: AIAssistant) {
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
                text = assistant.setupInstructions
                    ?: "No API key required — uses environment-based authentication.",
                style = DSFont.bodySmall,
                color = LocalDSColors.current.textSecondary,
                modifier = Modifier.padding(horizontal = DSSpacing.md, vertical = DSSpacing.sm),
            )
        }
    }
}

@Composable
private fun LlmInstallSection(assistant: AIAssistant) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.sm),
    ) {
        Text(
            text = "Installation",
            style = DSFont.buttonLabel,
            color = LocalDSColors.current.textSecondary,
        )

        assistant.prerequisite?.let { prereq ->
            Text(
                text = "Requires: $prereq",
                style = DSFont.sidebarItemSmall,
                color = LocalDSColors.current.textMuted,
            )
        }

        SettingsMonoBlock(text = assistant.installHint)
    }
}
