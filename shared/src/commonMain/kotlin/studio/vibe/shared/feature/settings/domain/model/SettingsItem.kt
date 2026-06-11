package studio.vibe.shared.feature.settings.domain.model

import studio.vibe.shared.core.common.AIAgent

public sealed class SettingsItem {
    public abstract val id: String
    public abstract val displayName: String
    public abstract val systemImage: String
    public abstract val sectionGroup: SettingsSectionGroup

    public data object Appearance : SettingsItem() {
        override val id = "appearance"
        override val displayName = "Appearance"
        override val systemImage = "folder.fill"
        override val sectionGroup = SettingsSectionGroup.GENERAL
    }

    public data object RemoteControl : SettingsItem() {
        override val id = "remoteControl"
        override val displayName = "Remote Control"
        override val systemImage = "antenna.radiowaves.left.and.right"
        override val sectionGroup = SettingsSectionGroup.GENERAL
    }

    /** Registry-aware item backed by [AIAgent]. Used by [SettingsSectionGroup.itemsFromRegistry]. */
    public data class LlmAgent(val agent: AIAgent) : SettingsItem() {
        override val id = "llm-${agent.id}"
        override val displayName = agent.displayName.replaceFirstChar { it.uppercase() }
        override val systemImage = "brain"
        override val sectionGroup = SettingsSectionGroup.LLM
    }
}
