package studio.vibe.shared.model

public enum class SidebarSection {
    FILES,
    GIT,
    SPECS,
}

public enum class SettingsSectionGroup(public val displayName: String) {
    GENERAL("General"),
    LLM("LLM");

    public val items: List<SettingsItem>
        get() = when (this) {
            GENERAL -> listOf(SettingsItem.Appearance, SettingsItem.RemoteControl)
            LLM -> AIAssistant.entries.map { SettingsItem.LlmAssistant(it) }
        }
}

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

    public data class LlmAssistant(val assistant: AIAssistant) : SettingsItem() {
        override val id = "llm-${assistant.id}"
        override val displayName = assistant.displayName.replaceFirstChar { it.uppercase() }
        override val systemImage = "brain"
        override val sectionGroup = SettingsSectionGroup.LLM
    }
}
