package studio.vibe.shared.feature.settings.domain.model

import studio.vibe.shared.core.common.AIAgentRegistry

public enum class SettingsSectionGroup(public val displayName: String) {
    GENERAL("General"),
    LLM("LLM");

    /**
     * Registry-aware items: LLM section is built from [registry] so plugin agents
     * appear automatically without modifying this file.
     */
    public fun itemsFromRegistry(registry: AIAgentRegistry): List<SettingsItem> = when (this) {
        GENERAL -> listOf(SettingsItem.Appearance, SettingsItem.RemoteControl)
        LLM -> registry.snapshot().map { SettingsItem.LlmAgent(it) }
    }
}
